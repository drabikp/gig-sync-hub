import { useTranslation } from 'react-i18next';
import { useLocation, useNavigate } from 'react-router';
import type { TFunction } from 'i18next';
import {
  type CalendarCommand,
  useCalendar,
  useCalendarAction,
  useCheck,
  useCheckAction,
  useGigAction,
  usePlatforms,
  useResync,
  useSyncAction,
  useSyncStatus,
  useSyncTasks,
} from '../api/hooks';
import type { CalendarRow, Drift } from '../api/types';
import { useBandPath } from '../band/band';
import { done, failed, queued } from './notify';
import { hhmm, shortDate, timeOfDay } from './format';

/** One thing to do, as a button. */
export interface Action {
  label: string;
  run: () => void;
  primary?: boolean;
  danger?: boolean;
  /** Asks first, with this text, before {@link run}. */
  confirm?: { title: string; text: string; yes: string };
}

/** What needs the user, with its fixes (the Inbox). */
export interface InboxItem {
  key: string;
  kind: 'failed' | 'held' | 'calendar' | 'drift';
  kicker: string;
  title: string;
  body: string;
  when?: string;
  gigId?: string;
  actions: Action[];
}

export function usePlatformName() {
  const platforms = usePlatforms();
  return (id: string) => platforms.data?.find((p) => p.id === id)?.name ?? id;
}

/** How the calendar and a linked gig differ, in the user's language. */
export function differenceText(t: TFunction, d: CalendarRow['match']['differences'][number]): string {
  switch (d.kind) {
    case 'DATE':
      return t('diff.date', { calendar: shortDate(d.calendar!), catalog: shortDate(d.catalog!) });
    case 'SHOW_TIME':
      return d.catalog === null
        ? t('diff.slotOnly', { calendar: `${shortDate(d.calendar!.split('T')[0])} ${timeOfDay(d.calendar!.split('T')[1])}` })
        : t('diff.showTime', { calendar: timeOfDay(d.calendar), catalog: timeOfDay(d.catalog) });
    case 'CANCELLED':
      return t('diff.cancelled');
    case 'REMOVED':
      return t('diff.removed');
  }
}

/** What changed in the calendar since the user last looked. */
export function changeText(t: TFunction, change: NonNullable<CalendarRow['change']>): string {
  if (change.type === 'CHANGED') {
    return t('change.CHANGED', { fields: change.fields.map((f) => t(`change.field.${f}`)).join(', ') });
  }
  return t(`change.${change.type}`);
}

/** One difference the platform check found, in the user's language. */
export function driftText(t: TFunction, d: Drift['differences'][number], platform: string): string {
  switch (d.field) {
    case 'DATE':
      return t('drift.DATE', { name: platform, catalog: shortDate(d.catalog!), platform: shortDate(d.platform!) });
    case 'CANCELLED':
      return d.catalog === 'true' ? t('drift.cancelledHere', { name: platform }) : t('drift.cancelledThere', { name: platform });
    case 'COUNTRY':
      return t('drift.COUNTRY', { name: platform, catalog: t(`country.${d.catalog}`), platform: t(`country.${d.platform}`) });
    default:
      return t(`drift.${d.field}`, { name: platform, catalog: d.catalog ?? '—', platform: d.platform ?? '—' });
  }
}

/**
 * What can be done about a calendar event: update, cancel or delete the linked gig the calendar
 * changed; add a gig the catalog misses (or link it to the gig that day); say whether it is a
 * gig; mark a change seen. {@code full}: also the quieter ones (unlink, undo) for the calendar page.
 */
export function useCalendarRowActions() {
  const path = useBandPath();
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  // the form goes back where it was opened from (the calendar's view, the Inbox)
  const from = { state: { back: location.pathname + location.search } };
  const calendar = useCalendarAction();
  const gigAction = useGigAction();
  const name = usePlatformName();
  const call = (command: CalendarCommand, message?: string) =>
    calendar.mutate(command, { onSuccess: () => message && done(message), onError: failed });

  return (row: CalendarRow, full = false): Action[] => {
    const actions: Action[] = [];
    const match = row.match;
    const has = (kind: string) => match.differences.some((d) => d.kind === kind);
    if (match.state === 'LINKED' && match.gig) {
      const gig = match.gig;
      if (has('DATE') || has('SHOW_TIME')) {
        const params = new URLSearchParams({ fromEvent: row.eventId, day: row.draft.date, moveDay: has('DATE') ? '1' : '0' });
        if (row.draft.showTime) params.set('time', hhmm(row.draft.showTime));
        actions.push({ label: t('calendar.updateGig'), primary: true, run: () => navigate(path(`/gig/${gig.id}/edit?${params}`), from) });
      }
      if ((has('CANCELLED') || has('REMOVED')) && !gig.cancelled) {
        actions.push({
          label: t('calendar.cancelGig'),
          danger: true,
          run: () =>
            gigAction.mutate({ id: gig.id, action: 'cancel' }, {
              onSuccess: (result) => {
                queued(t, t('toast.cancelled'), result, name);
                call({ kind: 'seen', event: row.eventId });
              },
              onError: failed,
            }),
        });
      }
      if (has('REMOVED')) {
        actions.push({
          label: t('calendar.deleteGig'),
          danger: true,
          confirm: { title: t('sheet.deleteTitle', { name: gig.title }), text: t('sheet.deleteFromEverywhere'), yes: t('sheet.confirmDelete') },
          run: () =>
            gigAction.mutate({ id: gig.id, action: 'delete' }, {
              onSuccess: (result) => {
                queued(t, t('toast.deleted'), result, name);
                call({ kind: 'seen', event: row.eventId });
              },
              onError: failed,
            }),
        });
        actions.push({ label: t('calendar.keepGig'), run: () => call({ kind: 'seen', event: row.eventId }, t('toast.done')) });
      }
    } else if (row.missingFromCatalog && row.status !== 'CANCELLED') {
      actions.push({ label: t('calendar.addGig'), primary: true, run: () => navigate(path(`/calendar/${encodeURIComponent(row.eventId)}/add`), from) });
      if (match.state === 'SAME_DAY' && match.gig) {
        actions.push({
          label: t('calendar.linkTo', { title: match.gig.title }),
          run: () => call({ kind: 'link', event: row.eventId, gig: match.gig!.id }, t('toast.linked')),
        });
      }
    }
    if (!row.removed) {
      if (row.decidedByUser) {
        if (full) actions.push({ label: t('calendar.undo'), run: () => call({ kind: 'forget', event: row.eventId }) });
      } else {
        if (row.kind !== 'GIG') {
          actions.push({ label: t('calendar.itsAGig'), primary: actions.length === 0,
            run: () => call({ kind: 'decide', event: row.eventId, verdict: 'GIG' }) });
        }
        if (row.kind !== 'NOT_GIG') {
          actions.push({ label: t('calendar.notAGig'),
            run: () => call({ kind: 'decide', event: row.eventId, verdict: 'NOT_GIG' }, t('toast.notGig')) });
        }
      }
    }
    if (full && match.state === 'LINKED' && !row.removed) {
      actions.push({ label: t('calendar.unlink'), run: () => call({ kind: 'unlink', event: row.eventId }) });
    }
    if (row.change && !has('REMOVED')) {
      actions.push({ label: t('calendar.seen'), run: () => call({ kind: 'seen', event: row.eventId }) });
    }
    return actions;
  };
}

/** What a calendar row is about, for its card: the differences, else the change, else why it is listed. */
export function calendarRowBody(t: TFunction, row: CalendarRow): string {
  if (row.match.differences.length) return row.match.differences.map((d) => differenceText(t, d)).join(' · ');
  if (row.missingFromCatalog) return t('inbox.calNewBody');
  if (row.kind === 'UNSURE') return t('inbox.calUnsureBody');
  if (row.change) return changeText(t, row.change);
  return '';
}

/** Everything that needs the user now, newest first, each with its fixes. */
export function useInbox() {
  const path = useBandPath();
  const { t } = useTranslation();
  const navigate = useNavigate();
  const name = usePlatformName();
  const failedTasks = useSyncTasks('failed');
  const status = useSyncStatus();
  const calendar = useCalendar();
  const check = useCheck();
  const sync = useSyncAction();
  const resync = useResync();
  const checkAction = useCheckAction();
  const rowActions = useCalendarRowActions();

  const items: InboxItem[] = [];
  for (const task of failedTasks.data ?? []) {
    items.push({
      key: `task-${task.id}`,
      kind: 'failed',
      kicker: t('inbox.failed'),
      title: t('inbox.onPlatform', { gig: task.gigLabel, platform: name(task.platform) }),
      body: `${t(`action.${task.action}`)}: ${task.message ?? ''}`,
      when: task.updatedAt,
      gigId: task.gigId,
      actions: [
        {
          label: t('inbox.retry'),
          primary: true,
          run: () => sync.mutate({ kind: 'retry', task: task.id }, { onSuccess: () => done(t('toast.retrying', { platform: name(task.platform) })), onError: failed }),
        },
        { label: t('inbox.openGig'), run: () => navigate(path(`/gig/${task.gigId}`)) },
        {
          label: t('inbox.discard'),
          confirm: { title: t('inbox.discardTitle'), text: t('inbox.discardText'), yes: t('inbox.discard') },
          run: () => sync.mutate({ kind: 'discard', task: task.id }, { onSuccess: () => done(t('toast.done')), onError: failed }),
        },
      ],
    });
  }
  for (const breaker of status.data?.breakers ?? []) {
    if (!breaker.held) continue;
    items.push({
      key: `held-${breaker.platform}`,
      kind: 'held',
      kicker: t('inbox.held'),
      title: t('inbox.heldTitle', { platform: name(breaker.platform) }),
      body: t('inbox.heldBody', { failures: breaker.failures, time: breaker.heldUntil ? new Date(breaker.heldUntil).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '', last: breaker.lastFailure ?? '' }),
      actions: [{
        label: t('inbox.resume'),
        primary: true,
        run: () => sync.mutate({ kind: 'resumePlatform', platform: breaker.platform }, { onSuccess: () => done(t('toast.resumed', { platform: name(breaker.platform) })), onError: failed }),
      }],
    });
  }
  for (const row of calendar.data?.rows ?? []) {
    if (!row.filters.includes('NEEDS_A_LOOK')) continue;
    items.push({
      key: `cal-${row.eventId}`,
      kind: 'calendar',
      kicker: row.match.differences.length ? t('inbox.calChanged') : t('inbox.calendar'),
      title: `${row.title} · ${shortDate(row.draft.date)}`,
      body: calendarRowBody(t, row),
      when: row.change?.at,
      gigId: row.match.gig?.id,
      actions: rowActions(row),
    });
  }
  for (const drift of check.data?.last?.drifts ?? []) {
    const platform = name(drift.platform);
    const ref = { platform: drift.platform, gig: drift.gigId };
    const dismiss = () => checkAction.mutate({ kind: 'dismiss', ref });
    items.push({
      key: `drift-${drift.platform}-${drift.gigId}`,
      kind: 'drift',
      kicker: t('inbox.drift'),
      title: t('inbox.onPlatform', { gig: drift.gigLabel, platform }),
      body: drift.kind === 'MISSING' ? t('drift.missing', { name: platform })
        : drift.differences.map((d) => driftText(t, d, platform)).join(' · '),
      when: check.data?.last?.checkedAt,
      gigId: drift.gigId,
      actions: drift.kind === 'MISSING'
        ? [
            { label: t('check.forget'), primary: true, run: () => checkAction.mutate({ kind: 'forget', ref }, { onSuccess: () => done(t('toast.unlinked')), onError: failed }) },
            { label: t('check.ignore'), run: dismiss },
          ]
        : [
            {
              label: t('check.fix'),
              primary: true,
              run: () => resync.mutate([drift.gigId], { onSuccess: (r) => { queued(t, '', r, name); dismiss(); }, onError: failed }),
            },
            { label: t('check.ignore'), run: dismiss },
          ],
    });
  }
  items.sort((a, b) => (b.when ?? '').localeCompare(a.when ?? ''));
  return {
    items,
    loading: failedTasks.isLoading || calendar.isLoading || check.isLoading || status.isLoading,
  };
}
