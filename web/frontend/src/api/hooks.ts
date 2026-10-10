import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useBandId } from '../band/band';
import { api, data } from './client';
import type { BandForm, CalendarKind, CalendarRuleDraft, DriftRef, GigDraft, ImportDecision, PlatformAccountForm } from './types';

/**
 * One TanStack Query hook per API resource; every call is typed by the OpenAPI specs (paths,
 * parameters, bodies and answers). A band's resources are the band's the page shows (its address,
 * {@link useBandId}), kept under `['band', <id>, …]` — switching bands never shows another band's
 * data. A change reads the affected parts again.
 */

/** A band's part of the cache: `['band', <id>, 'gigs', …]`. */
export function bandKey(bandId: string, ...rest: unknown[]): unknown[] {
  return ['band', bandId, ...rest];
}

// --- who is signed in, and their bands ------------------------------------------------------

export function useMe() {
  return useQuery({
    queryKey: ['me'],
    queryFn: async () => {
      try {
        return await data(api.GET('/api/auth/me'));
      } catch {
        return null;
      }
    },
    staleTime: Infinity,
  });
}

/** Ends the app's session, then the browser goes to Rauthy to end that one too (and comes back). */
export function useLogout() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: () => data(api.POST('/api/auth/logout')),
    onSuccess: (signedOut) => window.location.assign(signedOut.redirect),
    onError: () => {
      client.setQueryData(['me'], null);
      client.removeQueries({ predicate: (q) => q.queryKey[0] !== 'me' });
    },
  });
}

export function useMyBands(enabled = true) {
  return useQuery({ queryKey: ['bands'], queryFn: () => data(api.GET('/api/bands')), enabled });
}

export function useCreateBand() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (band: BandForm) => data(api.POST('/api/bands', { body: band })),
    onSuccess: () => client.invalidateQueries({ queryKey: ['bands'] }),
  });
}

export function useUpdateBand() {
  const bandId = useBandId();
  const client = useQueryClient();
  return useMutation({
    mutationFn: (band: BandForm) =>
      data(api.PUT('/api/bands/{bandId}', { params: { path: { bandId } }, body: band })),
    onSuccess: () => client.invalidateQueries({ queryKey: ['bands'] }),
  });
}

export function useMembers() {
  const bandId = useBandId();
  return useQuery({
    queryKey: bandKey(bandId, 'members'),
    queryFn: () => data(api.GET('/api/bands/{bandId}/members', { params: { path: { bandId } } })),
  });
}

/** The band's accounts on the platforms (a secret is never sent back — only that it is kept). */
export function usePlatformAccounts() {
  const bandId = useBandId();
  return useQuery({
    queryKey: bandKey(bandId, 'accounts'),
    queryFn: () => data(api.GET('/api/bands/{bandId}/platform-accounts', { params: { path: { bandId } } })),
  });
}

/** Connects or changes the band's account on a platform (owners); a blank secret keeps the one kept. */
export function useConnectPlatform() {
  const bandId = useBandId();
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ platform, account }: { platform: string; account: PlatformAccountForm }) =>
      data(api.PUT('/api/bands/{bandId}/platform-accounts/{platform}', {
        params: { path: { bandId, platform } }, body: account,
      })),
    onSuccess: () => client.invalidateQueries({ queryKey: bandKey(bandId, 'accounts') }),
  });
}

export function useDisconnectPlatform() {
  const bandId = useBandId();
  const client = useQueryClient();
  return useMutation({
    mutationFn: (platform: string) =>
      data(api.DELETE('/api/bands/{bandId}/platform-accounts/{platform}', { params: { path: { bandId, platform } } })),
    onSuccess: () => client.invalidateQueries({ queryKey: bandKey(bandId, 'accounts') }),
  });
}

/** Signs in to the platform with the band's account once (may take a minute); the answer says how it went. */
export function useTestPlatform() {
  const bandId = useBandId();
  const client = useQueryClient();
  return useMutation({
    mutationFn: (platform: string) =>
      data(api.POST('/api/bands/{bandId}/platform-accounts/{platform}/check', { params: { path: { bandId, platform } } })),
    onSettled: () => client.invalidateQueries({ queryKey: bandKey(bandId, 'accounts') }),
  });
}

// --- the catalog ---------------------------------------------------------------------------

export function usePlatforms() {
  return useQuery({ queryKey: ['platforms'], queryFn: () => data(api.GET('/api/platforms')), staleTime: Infinity });
}

export function useGigs() {
  const bandId = useBandId();
  return useQuery({
    queryKey: bandKey(bandId, 'gigs'),
    queryFn: () => data(api.GET('/api/bands/{bandId}/gigs', { params: { path: { bandId } } })),
  });
}

export function useGig(id: string | undefined) {
  const bandId = useBandId();
  return useQuery({
    queryKey: bandKey(bandId, 'gigs', id),
    queryFn: () => data(api.GET('/api/bands/{bandId}/gigs/{id}', { params: { path: { bandId, id: id! } } })),
    enabled: !!id,
  });
}

/** After any change to the catalog: the lists, the gig, the sync state and the calendar are read again. */
function useCatalogChanged() {
  const bandId = useBandId();
  const client = useQueryClient();
  return () => {
    client.invalidateQueries({ queryKey: bandKey(bandId, 'gigs') });
    client.invalidateQueries({ queryKey: bandKey(bandId, 'sync') });
    client.invalidateQueries({ queryKey: bandKey(bandId, 'calendar') });
  };
}

export function useAddGig() {
  const bandId = useBandId();
  const changed = useCatalogChanged();
  return useMutation({
    mutationFn: (gig: GigDraft) => data(api.POST('/api/bands/{bandId}/gigs', { params: { path: { bandId } }, body: gig })),
    onSuccess: changed,
  });
}

export function useEditGig() {
  const bandId = useBandId();
  const changed = useCatalogChanged();
  return useMutation({
    mutationFn: (edit: { id: string; rev: string; gig: GigDraft }) =>
      data(api.PUT('/api/bands/{bandId}/gigs/{id}', {
        params: { path: { bandId, id: edit.id } }, body: { rev: edit.rev, gig: edit.gig },
      })),
    onSuccess: changed,
  });
}

export function useGigAction() {
  const bandId = useBandId();
  const changed = useCatalogChanged();
  return useMutation({
    mutationFn: ({ id, action }: { id: string; action: 'cancel' | 'reactivate' | 'delete' }) => {
      const params = { params: { path: { bandId, id } } };
      return data(action === 'delete' ? api.DELETE('/api/bands/{bandId}/gigs/{id}', params)
        : action === 'cancel' ? api.POST('/api/bands/{bandId}/gigs/{id}/cancel', params)
          : api.POST('/api/bands/{bandId}/gigs/{id}/reactivate', params));
    },
    onSuccess: changed,
  });
}

export function usePublish() {
  const bandId = useBandId();
  const changed = useCatalogChanged();
  return useMutation({
    mutationFn: (selection: { gigs: string[]; platforms: string[] }) =>
      data(api.POST('/api/bands/{bandId}/gigs/publish', { params: { path: { bandId } }, body: selection })),
    onSuccess: changed,
  });
}

export function useResync() {
  const bandId = useBandId();
  const changed = useCatalogChanged();
  return useMutation({
    mutationFn: (gigs: string[]) =>
      data(api.POST('/api/bands/{bandId}/gigs/resync', { params: { path: { bandId } }, body: { gigs } })),
    onSuccess: changed,
  });
}

/** The address a platform's file downloads from (a page load, not an API call). */
export function useExportAddress(): (platform: string) => string {
  const bandId = useBandId();
  return (platform) => `/api/bands/${bandId}/exports/${encodeURIComponent(platform)}`;
}

export function useTowns(query: string) {
  return useQuery({
    queryKey: ['places', query],
    queryFn: () => data(api.GET('/api/places', { params: { query: { q: query } } })),
    enabled: query.trim().length >= 2,
    staleTime: 5 * 60_000,
  });
}

export function resolveTown(name: string, country: GigDraft['country'], postalCode: string | null) {
  return data(api.GET('/api/places/resolve', {
    params: { query: { name, country: country ?? undefined, postalCode: postalCode ?? undefined } },
  }));
}

// --- the sync ------------------------------------------------------------------------------

export function useSyncTasks(show: 'all' | 'open' | 'failed') {
  const bandId = useBandId();
  return useQuery({
    queryKey: bandKey(bandId, 'sync', 'tasks', show),
    queryFn: () => data(api.GET('/api/bands/{bandId}/sync/tasks', { params: { path: { bandId }, query: { show } } })),
  });
}

export function useTaskLog(id: number | null) {
  const bandId = useBandId();
  return useQuery({
    queryKey: bandKey(bandId, 'sync', 'log', id),
    queryFn: () => data(api.GET('/api/bands/{bandId}/sync/tasks/{id}/log', { params: { path: { bandId, id: id! } } })),
    enabled: id !== null,
  });
}

export function useSyncStatus() {
  const bandId = useBandId();
  return useQuery({
    queryKey: bandKey(bandId, 'sync', 'status'),
    queryFn: () => data(api.GET('/api/bands/{bandId}/sync/status', { params: { path: { bandId } } })),
  });
}

/** What the user can tell the sync. */
export type SyncCommand =
  | { kind: 'retry'; task: number }
  | { kind: 'discard'; task: number }
  | { kind: 'pause' }
  | { kind: 'resume' }
  | { kind: 'resumePlatform'; platform: string };

function syncCall(bandId: string, command: SyncCommand) {
  const band = { params: { path: { bandId } } };
  switch (command.kind) {
    case 'retry':
      return api.POST('/api/bands/{bandId}/sync/tasks/{id}/retry', { params: { path: { bandId, id: command.task } } });
    case 'discard':
      return api.POST('/api/bands/{bandId}/sync/tasks/{id}/discard', { params: { path: { bandId, id: command.task } } });
    case 'pause':
      return api.POST('/api/bands/{bandId}/sync/pause', band);
    case 'resume':
      return api.POST('/api/bands/{bandId}/sync/resume', band);
    case 'resumePlatform':
      return api.POST('/api/bands/{bandId}/sync/breakers/{platform}/resume', {
        params: { path: { bandId, platform: command.platform } },
      });
  }
}

export function useSyncAction() {
  const bandId = useBandId();
  const changed = useCatalogChanged();
  return useMutation({ mutationFn: (command: SyncCommand) => data(syncCall(bandId, command)), onSuccess: changed });
}

// --- the calendar --------------------------------------------------------------------------

export function useCalendar() {
  const bandId = useBandId();
  return useQuery({
    queryKey: bandKey(bandId, 'calendar'),
    queryFn: () => data(api.GET('/api/bands/{bandId}/calendar', { params: { path: { bandId } } })),
  });
}

export function useCalendarRules(enabled: boolean) {
  const bandId = useBandId();
  return useQuery({
    queryKey: bandKey(bandId, 'calendar', 'rules'),
    queryFn: () => data(api.GET('/api/bands/{bandId}/calendar/rules', { params: { path: { bandId } } })),
    enabled,
  });
}

/** The kinds of rule there are (for the rules editor). */
export function useRuleKinds(enabled: boolean) {
  const bandId = useBandId();
  return useQuery({
    queryKey: bandKey(bandId, 'calendar', 'rule-kinds'),
    queryFn: () => data(api.GET('/api/bands/{bandId}/calendar/rule-kinds', { params: { path: { bandId } } })),
    staleTime: Infinity,
    enabled,
  });
}

/** Replaces the band's own rules; the calendar is sorted with them from then on. */
export function useSaveCalendarRules() {
  const bandId = useBandId();
  const client = useQueryClient();
  return useMutation({
    mutationFn: (rules: CalendarRuleDraft[]) =>
      data(api.PUT('/api/bands/{bandId}/calendar/rules', { params: { path: { bandId } }, body: rules })),
    onSuccess: (rules) => {
      client.setQueryData(bandKey(bandId, 'calendar', 'rules'), rules);
      client.invalidateQueries({ queryKey: bandKey(bandId, 'calendar'), exact: true });
    },
  });
}

/** Which calendar the band reads (whose — never its address — and its zone). */
export function useCalendarConnection() {
  const bandId = useBandId();
  return useQuery({
    queryKey: bandKey(bandId, 'calendar', 'connection'),
    queryFn: () => data(api.GET('/api/bands/{bandId}/calendar/connection', { params: { path: { bandId } } })),
  });
}

export function useConnectCalendar() {
  const bandId = useBandId();
  const client = useQueryClient();
  return useMutation({
    mutationFn: (calendar: { address: string; zone: string | null }) =>
      data(api.PUT('/api/bands/{bandId}/calendar/connection', { params: { path: { bandId } }, body: calendar })),
    onSuccess: () => client.invalidateQueries({ queryKey: bandKey(bandId, 'calendar') }),
  });
}

export function useDisconnectCalendar() {
  const bandId = useBandId();
  const client = useQueryClient();
  return useMutation({
    mutationFn: () => data(api.DELETE('/api/bands/{bandId}/calendar/connection', { params: { path: { bandId } } })),
    onSuccess: () => client.invalidateQueries({ queryKey: bandKey(bandId, 'calendar') }),
  });
}

export function useReadCalendar() {
  const bandId = useBandId();
  const client = useQueryClient();
  return useMutation({
    mutationFn: () => data(api.POST('/api/bands/{bandId}/calendar/read', { params: { path: { bandId } } })),
    onSuccess: (overview) => client.setQueryData(bandKey(bandId, 'calendar'), overview),
  });
}

/** What the user can tell about the calendar's events. */
export type CalendarCommand =
  | { kind: 'decide'; event: string; verdict: CalendarKind }
  | { kind: 'forget'; event: string }
  | { kind: 'seen'; event: string }
  | { kind: 'seenAll' }
  | { kind: 'link'; event: string; gig: string }
  | { kind: 'unlink'; event: string }
  | { kind: 'linkSameDay' };

/** The answer: how many were linked (link-same-day), nothing otherwise. */
async function calendarCall(bandId: string, command: CalendarCommand): Promise<{ linked: number } | undefined> {
  const band = { params: { path: { bandId } } };
  const event = (eventId: string) => ({ params: { path: { bandId, eventId } } });
  switch (command.kind) {
    case 'decide':
      return data(api.POST('/api/bands/{bandId}/calendar/events/{eventId}/decide', {
        ...event(command.event), body: { kind: command.verdict },
      }));
    case 'forget':
      return data(api.POST('/api/bands/{bandId}/calendar/events/{eventId}/forget', event(command.event)));
    case 'seen':
      return data(api.POST('/api/bands/{bandId}/calendar/events/{eventId}/seen', event(command.event)));
    case 'seenAll':
      return data(api.POST('/api/bands/{bandId}/calendar/seen', band));
    case 'link':
      return data(api.POST('/api/bands/{bandId}/calendar/events/{eventId}/link', {
        ...event(command.event), body: { gig: command.gig },
      }));
    case 'unlink':
      return data(api.POST('/api/bands/{bandId}/calendar/events/{eventId}/unlink', event(command.event)));
    case 'linkSameDay':
      return data(api.POST('/api/bands/{bandId}/calendar/link-same-day', band));
  }
}

export function useCalendarAction() {
  const bandId = useBandId();
  const changed = useCatalogChanged();
  return useMutation({ mutationFn: (command: CalendarCommand) => calendarCall(bandId, command), onSuccess: changed });
}

export function useAddFromCalendar() {
  const bandId = useBandId();
  const changed = useCatalogChanged();
  return useMutation({
    mutationFn: ({ eventId, gig }: { eventId: string; gig: GigDraft }) =>
      data(api.POST('/api/bands/{bandId}/calendar/events/{eventId}/gig', {
        params: { path: { bandId, eventId } }, body: { gig },
      })),
    onSuccess: changed,
  });
}

// --- the platform check and import ---------------------------------------------------------

export function useCheck() {
  const bandId = useBandId();
  return useQuery({
    queryKey: bandKey(bandId, 'check'),
    queryFn: () => data(api.GET('/api/bands/{bandId}/check', { params: { path: { bandId } } })),
  });
}

/** What the user can tell the platform check. */
export type CheckCommand = { kind: 'run' } | { kind: 'forget'; ref: DriftRef } | { kind: 'dismiss'; ref: DriftRef };

function checkCall(bandId: string, command: CheckCommand) {
  const band = { params: { path: { bandId } } };
  switch (command.kind) {
    case 'run':
      return api.POST('/api/bands/{bandId}/check/run', band);
    case 'forget':
      return api.POST('/api/bands/{bandId}/check/forget', { ...band, body: command.ref });
    case 'dismiss':
      return api.POST('/api/bands/{bandId}/check/dismiss', { ...band, body: command.ref });
  }
}

export function useCheckAction() {
  const bandId = useBandId();
  const client = useQueryClient();
  return useMutation({
    mutationFn: (command: CheckCommand) => data(checkCall(bandId, command)),
    onSuccess: (_, command) => {
      client.invalidateQueries({ queryKey: bandKey(bandId, 'check') });
      // forgetting a link changes the gig's state on that platform
      if (command.kind === 'forget') client.invalidateQueries({ queryKey: bandKey(bandId, 'gigs') });
    },
  });
}

export function useImport() {
  const bandId = useBandId();
  return useQuery({
    queryKey: bandKey(bandId, 'import'),
    queryFn: () => data(api.GET('/api/bands/{bandId}/import', { params: { path: { bandId } } })),
  });
}

export function useReadForImport() {
  const bandId = useBandId();
  const client = useQueryClient();
  return useMutation({
    mutationFn: (platforms: string[]) =>
      data(api.POST('/api/bands/{bandId}/import/read', { params: { path: { bandId } }, body: { platforms } })),
    onSuccess: () => client.invalidateQueries({ queryKey: bandKey(bandId, 'import') }),
  });
}

export function useApplyImport() {
  const bandId = useBandId();
  const changed = useCatalogChanged();
  const client = useQueryClient();
  return useMutation({
    mutationFn: (decisions: ImportDecision[]) =>
      data(api.POST('/api/bands/{bandId}/import/apply', { params: { path: { bandId } }, body: { decisions } })),
    onSuccess: () => {
      changed();
      client.invalidateQueries({ queryKey: bandKey(bandId, 'import') });
    },
  });
}
