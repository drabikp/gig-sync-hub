import {
  ActionIcon,
  Box,
  Alert,
  Anchor,
  Avatar,
  Badge,
  Button,
  Group,
  Paper,
  SimpleGrid,
  Skeleton,
  Stack,
  Switch,
  Text,
  Timeline,
  Title,
} from '@mantine/core';
import {
  IconCalendarEvent,
  IconDotsVertical,
  IconExternalLink,
  IconMapPin,
  IconPencil,
} from '@tabler/icons-react';
import { useEffect, useState } from 'react';
import type { TFunction } from 'i18next';
import { useTranslation } from 'react-i18next';
import { useLocation, useNavigate, useParams } from 'react-router';
import { useCalendar, useGig, useGigAction, usePlatforms, usePublish, useResync, useSyncAction } from '../api/hooks';
import type { Gig, Platform, PlatformState } from '../api/types';
import { useBandPath } from '../band/band';
import { DateBlock } from '../components/DateBlock';
import { PageHeader } from '../components/PageHeader';
import { Sheet } from '../components/Sheet';
import { StatusChip } from '../components/StatusChip';
import { usePlatformName } from '../lib/attention';
import { clock, longDate, shortDate, timeOfDay, when } from '../lib/format';
import { done, failed, queued } from '../lib/notify';
import { useIsMobile } from '../lib/useIsMobile';

/** A platform's badge colour, steady per platform (the page knows platforms only as data). */
function logoColor(id: string): string {
  const colours = ['#1F2A44', '#0E7C86', '#7048E8', '#C2255C', '#2B8A3E', '#E8590C'];
  let h = 0;
  for (const c of id) h = (h * 31 + c.charCodeAt(0)) >>> 0;
  return colours[h % colours.length];
}

/** What happens to the gig on each platform when it is cancelled, from the platforms' traits. */
function cancelText(t: TFunction, gig: Gig, platforms: Platform[]): string[] {
  const on = platforms.filter((p) => gig.platforms.find((s) => s.platform === p.id)?.state !== 'none');
  if (!on.length) return [t('sheet.onlyHere')];
  const keeps = on.filter((p) => p.keepsCancelledEvents).map((p) => p.name);
  const removes = on.filter((p) => !p.keepsCancelledEvents).map((p) => p.name);
  return [
    keeps.length ? t('sheet.keeps', { list: keeps.join(', ') }) : '',
    removes.length ? t('sheet.removes', { list: removes.join(', ') }) : '',
  ].filter(Boolean);
}

function PlatformCard({ gig, state, platform }: { gig: Gig; state: PlatformState; platform: Platform | undefined }) {
  const { t } = useTranslation();
  const name = platform?.name ?? state.platform;
  const publish = usePublish();
  const sync = useSyncAction();
  const busy = state.state === 'queued' || state.state === 'running' || state.state === 'retrying';
  const sub =
    state.state === 'live' ? (state.ref ? t('gig.event', { ref: state.ref }) : t('gig.published'))
      : state.state === 'failed' ? t('gig.failedAction', { action: t(`action.${state.action}`) })
        : state.state === 'retrying' ? t('gig.retryAt', { time: clock(state.nextAttemptAt) })
          : busy ? t(`gig.busy.${state.action}`)
            : gig.gig.cancelled && platform && !platform.keepsCancelledEvents ? t('gig.removedWhenCancelled') : t('gig.notThere');
  return (
    <Paper withBorder p="md" radius="lg" style={state.state === 'failed' ? { borderColor: 'var(--mantine-color-red-3)' } : undefined}>
      <Stack gap="sm">
        <Group wrap="nowrap" gap="sm">
          <Avatar radius="md" size={42} styles={{ placeholder: { background: logoColor(state.platform), color: '#fff', fontSize: 13, fontWeight: 800 } }}>
            {name.substring(0, 1).toUpperCase()}
          </Avatar>
          <Stack gap={0} style={{ flex: 1, minWidth: 0 }}>
            <Text fw={700}>{name}</Text>
            <Text size="sm" c="dimmed">
              {sub}
            </Text>
          </Stack>
          <StatusChip state={state.state} />
        </Group>
        {state.state === 'failed' && state.message && (
          <Alert color="red" variant="light" p="sm">
            {state.message}
          </Alert>
        )}
        <Group gap="xs">
          {state.state === 'failed' && state.taskId && (
            <>
              <Button size="sm" loading={sync.isPending}
                onClick={() => sync.mutate({ kind: 'retry', task: state.taskId! }, { onSuccess: () => done(t('toast.retrying', { platform: name })), onError: failed })}>
                {t('gig.retry')}
              </Button>
              <Button size="sm" variant="default"
                onClick={() => sync.mutate({ kind: 'discard', task: state.taskId! }, { onSuccess: () => done(t('toast.done')), onError: failed })}>
                {t('gig.discard')}
              </Button>
            </>
          )}
          {state.state === 'none' && !gig.gig.cancelled && (
            <Button size="sm" loading={publish.isPending}
              onClick={() => publish.mutate({ gigs: [gig.id], platforms: [state.platform] }, { onSuccess: (r) => queued(t, '', r, () => name), onError: failed })}>
              {t('gig.publish')}
            </Button>
          )}
          {state.url && state.state !== 'none' && (
            <Button size="sm" variant="default" component="a" href={state.url} target="_blank" rel="noopener"
              rightSection={<IconExternalLink size={16} />}>
              {t('gig.open')}
            </Button>
          )}
        </Group>
      </Stack>
    </Paper>
  );
}

export function GigPage() {
  const path = useBandPath();
  const { id } = useParams();
  const { t } = useTranslation();
  const mobile = useIsMobile();
  const navigate = useNavigate();
  const location = useLocation();
  const detail = useGig(id);
  const platforms = usePlatforms();
  const calendar = useCalendar();
  const action = useGigAction();
  const resync = useResync();
  const publish = usePublish();
  const name = usePlatformName();
  const [sheet, setSheet] = useState<null | 'actions' | 'cancel' | 'delete' | 'publish'>(null);
  const [targets, setTargets] = useState<string[]>([]);

  const gig = detail.data?.gig;
  useEffect(() => {
    if (gig && (location.state as { justAdded?: boolean } | null)?.justAdded) {
      setTargets(gig.platforms.filter((p) => p.state === 'none').map((p) => p.platform));
      setSheet('publish');
      navigate('.', { replace: true, state: null });
    }
  }, [gig, location.state, navigate]);

  if (detail.isError) {
    return (
      <Stack>
        <PageHeader title={t('gig.gone')} back="/" />
        <Text c="dimmed">{t('gig.goneText')}</Text>
      </Stack>
    );
  }
  if (!gig) return <Skeleton h={240} radius="lg" />;

  const d = gig.gig;
  const all = platforms.data ?? [];
  const published = gig.platforms.filter((p) => p.state !== 'none');
  const linkedEvent = calendar.data?.rows.find((r) => r.match.state === 'LINKED' && r.match.gig?.id === gig.id);
  const facts: [string, string][] = [
    [t('gig.starts'), `${shortDate(d.date!)} ${timeOfDay(d.time)}`],
    [t('gig.ends'), d.endTime ? `${d.endDate ? shortDate(d.endDate) + ' ' : ''}${timeOfDay(d.endTime)}` : '—'],
    [t('gig.slot'), d.slotTime ? `${shortDate(d.slotDate!)} ${timeOfDay(d.slotTime)}${d.slotEndTime ? '–' + timeOfDay(d.slotEndTime) : ''}` : '—'],
    [t('gig.venue'), d.venue || t('gig.tba')],
    [t('gig.town'), [d.city, d.district, d.country ? t(`country.${d.country}`) : null].filter(Boolean).join(' · ')],
    [t('gig.entry'), t(`entry.${d.entry}`) + (d.entry === 'PAID' && d.price ? ` · ${d.price}` : '')],
  ];

  const run = (what: 'cancel' | 'reactivate' | 'delete') =>
    action.mutate({ id: gig.id, action: what }, {
      onSuccess: (r) => {
        setSheet(null);
        queued(t, t(`toast.${what === 'delete' ? 'deleted' : what === 'cancel' ? 'cancelled' : 'reactivated'}`), r, name);
        if (what === 'delete') navigate(path('/'), { replace: true });
      },
      onError: failed,
    });

  const header = (
    <PageHeader
      title={d.title}
      back="/"
      backLabel={t('nav.gigs')}
      actions={
        <>
          <Button leftSection={<IconPencil size={18} />} onClick={() => navigate(path(`/gig/${gig.id}/edit`))}>
            {t('gig.edit')}
          </Button>
          <ActionIcon variant="default" size={42} aria-label={t('gig.moreActions')} onClick={() => setSheet('actions')}>
            <IconDotsVertical size={20} />
          </ActionIcon>
        </>
      }
      mobileAction={
        <ActionIcon variant="subtle" color="gray" size={44} aria-label={t('gig.moreActions')} onClick={() => setSheet('actions')}>
          <IconDotsVertical size={22} />
        </ActionIcon>
      }
    />
  );

  const platformCards = (
    <Stack gap="sm">
      <Title order={2} fz={13} c="dimmed" tt="uppercase" style={{ letterSpacing: '0.06em' }}>
        {t('gig.onPlatforms')}
      </Title>
      {gig.platforms.map((s) => (
        <PlatformCard key={s.platform} gig={gig} state={s} platform={all.find((p) => p.id === s.platform)} />
      ))}
    </Stack>
  );

  const details = (
    <Paper withBorder p="lg">
      <Title order={2} fz={13} c="dimmed" tt="uppercase" mb="md" style={{ letterSpacing: '0.06em' }}>
        {t('gig.details')}
      </Title>
      <SimpleGrid cols={2} spacing="sm" verticalSpacing="sm" style={{ gridTemplateColumns: 'minmax(0,1fr) minmax(0,2fr)' }}>
        {facts.map(([k, v]) => (
          <div key={k} style={{ display: 'contents' }}>
            <Text c="dimmed">{k}</Text>
            <Text fw={600}>{v}</Text>
          </div>
        ))}
      </SimpleGrid>
      {d.lineup.length > 0 && (
        <Stack gap={6} mt="md">
          <Text c="dimmed">{t('gig.lineup')}</Text>
          <Group gap={6}>
            {d.lineup.map((b) => (
              <Badge key={b} variant="default" size="lg" radius="xl" styles={{ label: { textTransform: 'none' } }}>
                {b}
              </Badge>
            ))}
          </Group>
        </Stack>
      )}
      {(d.ticketUrl || d.facebookUrl) && (
        <Group gap="md" mt="md">
          {d.ticketUrl && <Anchor href={d.ticketUrl} target="_blank" rel="noopener">{t('form.ticketUrl')}</Anchor>}
          {d.facebookUrl && <Anchor href={d.facebookUrl} target="_blank" rel="noopener">{t('form.facebookUrl')}</Anchor>}
        </Group>
      )}
      {d.description && (
        <Text mt="md" style={{ whiteSpace: 'pre-wrap' }}>
          {d.description}
        </Text>
      )}
    </Paper>
  );

  const side = (
    <Stack gap="md">
      {calendar.data?.configured && (
        <Paper withBorder p="md">
          <Group wrap="nowrap" gap="sm">
            <Avatar radius="md" color={linkedEvent ? 'teal' : 'gray'} variant="light">
              <IconCalendarEvent size={20} />
            </Avatar>
            <div>
              <Text size="xs" fw={700} c="dimmed" tt="uppercase">{t('gig.calendar')}</Text>
              <Text fw={600}>{linkedEvent ? t('gig.linkedTo', { name: linkedEvent.title }) : t('gig.notLinked')}</Text>
            </div>
          </Group>
        </Paper>
      )}
      <Paper withBorder p="lg">
        <Title order={2} fz={13} c="dimmed" tt="uppercase" mb="md" style={{ letterSpacing: '0.06em' }}>
          {t('gig.history')}
        </Title>
        {detail.data!.history.length === 0 ? (
          <Text c="dimmed" size="sm">{t('gig.noHistory')}</Text>
        ) : (
          <Timeline bulletSize={12} lineWidth={2}>
            {detail.data!.history.slice(0, 12).map((h) => (
              <Timeline.Item key={h.taskId}
                bullet={<Box w={10} h={10} style={{ borderRadius: 99, background: `var(--mantine-color-${h.status === 'FAILED' ? 'red' : h.status === 'DONE' ? 'teal' : h.status === 'DISCARDED' ? 'gray' : 'orange'}-6)` }} />}
                title={t(`history.${h.status}`, { action: t(`action.${h.action}`), platform: name(h.platform) })}>
                <Text size="sm" c="dimmed">{when(h.at)}</Text>
                {h.message && h.status !== 'DONE' && <Text size="sm" c="dimmed" lineClamp={2}>{h.message}</Text>}
              </Timeline.Item>
            ))}
          </Timeline>
        )}
      </Paper>
    </Stack>
  );

  return (
    <Stack gap="md">
      {header}
      <Paper withBorder p="md">
        <Group wrap="nowrap" gap="md">
          <DateBlock date={d.date!} muted={d.cancelled} size="lg" />
          <Stack gap={4} style={{ flex: 1, minWidth: 0 }}>
            <Text fw={600}>
              {longDate(d.date!)} · {timeOfDay(d.time)}
              {d.endTime ? `–${timeOfDay(d.endTime)}` : ''}
            </Text>
            <Group gap={6} c="dimmed" wrap="nowrap">
              <IconMapPin size={16} style={{ flexShrink: 0 }} />
              <Text size="sm" truncate>
                {d.venue || t('gig.tba')}, {d.city}
              </Text>
            </Group>
            {d.cancelled && <Badge color="gray" variant="light">{t('gig.cancelled')}</Badge>}
          </Stack>
        </Group>
        {mobile && (
          <Button fullWidth mt="md" variant="light" leftSection={<IconPencil size={18} />} onClick={() => navigate(path(`/gig/${gig.id}/edit`))}>
            {t('gig.edit')}
          </Button>
        )}
      </Paper>

      {mobile ? (
        <Stack gap="md">
          {platformCards}
          {details}
          {side}
        </Stack>
      ) : (
        <SimpleGrid cols={2} spacing="lg" style={{ gridTemplateColumns: 'minmax(0,1.5fr) minmax(0,1fr)', alignItems: 'start' }}>
          <Stack gap="md">
            {platformCards}
            {details}
          </Stack>
          {side}
        </SimpleGrid>
      )}

      <Sheet opened={sheet === 'actions'} onClose={() => setSheet(null)} title={d.title}>
        <Stack gap={4}>
          <Button variant="subtle" justify="flex-start" size="md" onClick={() => navigate(path(`/gig/${gig.id}/edit`))}>
            {t('gig.edit')}
          </Button>
          {published.length > 0 && !d.cancelled && (
            <Button variant="subtle" justify="flex-start" size="md"
              onClick={() => resync.mutate([gig.id], { onSuccess: (r) => { setSheet(null); queued(t, '', r, name); }, onError: failed })}>
              {t('gig.updateEverywhere')}
            </Button>
          )}
          {gig.platforms.some((p) => p.state === 'none') && !d.cancelled && (
            <Button variant="subtle" justify="flex-start" size="md" onClick={() => {
              setTargets(gig.platforms.filter((p) => p.state === 'none').map((p) => p.platform));
              setSheet('publish');
            }}>
              {t('gig.publishMore')}
            </Button>
          )}
          {d.cancelled ? (
            <Button variant="subtle" justify="flex-start" size="md" onClick={() => run('reactivate')}>
              {t('gig.reactivate')}
            </Button>
          ) : (
            <Button variant="subtle" color="red" justify="flex-start" size="md" onClick={() => setSheet('cancel')}>
              {t('gig.cancelGig')}
            </Button>
          )}
          <Button variant="subtle" color="red" justify="flex-start" size="md" onClick={() => setSheet('delete')}>
            {t('gig.delete')}
          </Button>
          <Button variant="default" size="md" mt="sm" onClick={() => setSheet(null)}>
            {t('common.close')}
          </Button>
        </Stack>
      </Sheet>

      <Sheet opened={sheet === 'cancel'} onClose={() => setSheet(null)} title={t('sheet.cancelTitle', { name: d.title })}>
        <Stack>
          {cancelText(t, gig, all).map((line) => <Text key={line}>{line}</Text>)}
          <Group grow>
            <Button variant="default" onClick={() => setSheet(null)}>{t('sheet.keep')}</Button>
            <Button color="red" loading={action.isPending} onClick={() => run('cancel')}>{t('gig.cancelGig')}</Button>
          </Group>
        </Stack>
      </Sheet>

      <Sheet opened={sheet === 'delete'} onClose={() => setSheet(null)} title={t('sheet.deleteTitle', { name: d.title })}>
        <Stack>
          <Text>
            {published.length
              ? t('sheet.deleteBody', { list: published.map((p) => name(p.platform)).join(', ') })
              : t('sheet.deleteLocal')}
          </Text>
          <Group grow>
            <Button variant="default" onClick={() => setSheet(null)}>{t('sheet.keep')}</Button>
            <Button color="red" loading={action.isPending} onClick={() => run('delete')}>{t('sheet.confirmDelete')}</Button>
          </Group>
        </Stack>
      </Sheet>

      <Sheet opened={sheet === 'publish'} onClose={() => setSheet(null)} title={t('sheet.publishTitle', { name: d.title })}>
        <Stack>
          <Text c="dimmed">{t('sheet.publishBody')}</Text>
          {gig.platforms.filter((p) => p.state === 'none').map((p) => (
            <Switch key={p.platform} size="lg" label={name(p.platform)} checked={targets.includes(p.platform)}
              onChange={() => setTargets(targets.includes(p.platform) ? targets.filter((x) => x !== p.platform) : [...targets, p.platform])} />
          ))}
          <Group grow mt="sm">
            <Button variant="default" onClick={() => setSheet(null)}>{t('sheet.later')}</Button>
            <Button disabled={!targets.length} loading={publish.isPending}
              onClick={() => publish.mutate({ gigs: [gig.id], platforms: targets }, {
                onSuccess: (r) => { setSheet(null); queued(t, '', r, name); },
                onError: failed,
              })}>
              {t('sheet.publish')}
            </Button>
          </Group>
        </Stack>
      </Sheet>
    </Stack>
  );
}
