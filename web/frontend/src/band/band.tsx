import { createContext, useCallback, useContext, type ReactNode } from 'react';
import type { MyBand } from '../api/types';

/**
 * The band the page shows — the one in its address (/b/<slug>/…). Every band's data is read and
 * changed through it; switching bands is going to another address.
 */
const BandContext = createContext<MyBand | null>(null);

export function BandProvider({ band, children }: { band: MyBand; children: ReactNode }) {
  return <BandContext.Provider value={band}>{children}</BandContext.Provider>;
}

/** The band the page shows (only inside a band's pages). */
export function useBand(): MyBand {
  const band = useContext(BandContext);
  if (!band) throw new Error('useBand outside a band’s pages');
  return band;
}

export function useBandId(): string {
  return useBand().id;
}

/** A band page's address: `bandPath('/gig/x')` → `/b/<slug>/gig/x`; `bandPath('/')` → `/b/<slug>`. */
export function useBandPath(): (path: string) => string {
  const { slug } = useBand();
  return useCallback((path: string) => bandAddress(slug, path), [slug]);
}

export function bandAddress(slug: string, path = '/'): string {
  return `/b/${slug}${path === '/' ? '' : path}`;
}

const LAST_BAND = 'bzscraper.band';

/** The band used last on this device (its slug), to start there next time. */
export function lastBand(): string | null {
  try {
    return localStorage.getItem(LAST_BAND);
  } catch {
    return null;
  }
}

export function rememberBand(slug: string) {
  try {
    localStorage.setItem(LAST_BAND, slug);
  } catch {
    // not remembered; the first band then
  }
}
