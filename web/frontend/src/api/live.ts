import { useEffect } from 'react';
import { QueryClient, useQueryClient } from '@tanstack/react-query';
import { bandKey } from './hooks';
import type { LiveTopic } from './types';

/** What each live event (live.yaml's LiveTopic — every one must be here) makes the page read again. */
const READ_AGAIN: Record<LiveTopic, string[]> = {
  gigs: ['gigs'],
  sync: ['sync', 'gigs'],
  check: ['check'],
  import: ['import'],
  calendar: ['calendar'],
};

function refresh(client: QueryClient, bandId: string, topic: LiveTopic) {
  READ_AGAIN[topic].forEach((part) => client.invalidateQueries({ queryKey: bandKey(bandId, part) }));
}

/**
 * Follows the band's live updates (server-sent events, /api/bands/<id>/events) while its pages are
 * open: each event names what changed, and that part is read again. A dropped stream is opened
 * again (the browser retries on its own; after the phone woke the app up, it is reopened) and the
 * band's data is read again, since events may have been missed meanwhile.
 */
export function useLiveUpdates(bandId: string) {
  const client = useQueryClient();
  useEffect(() => {
    if (typeof EventSource === 'undefined') return;
    let source: EventSource | null = null;
    let opened = false;
    const readEverything = () => client.invalidateQueries({ queryKey: bandKey(bandId) });
    const open = () => {
      source?.close();
      source = new EventSource(`/api/bands/${bandId}/events`);
      source.addEventListener('hello', () => {
        if (opened) readEverything();
        opened = true;
      });
      (Object.keys(READ_AGAIN) as LiveTopic[]).forEach((topic) =>
        source!.addEventListener(topic, () => refresh(client, bandId, topic)));
    };
    const wake = () => {
      if (document.visibilityState === 'visible' && (!source || source.readyState === EventSource.CLOSED)) {
        open();
        readEverything();
      }
    };
    open();
    document.addEventListener('visibilitychange', wake);
    return () => {
      document.removeEventListener('visibilitychange', wake);
      source?.close();
    };
  }, [bandId, client]);
}
