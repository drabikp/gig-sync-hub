import createClient from 'openapi-fetch';
import type { paths as AccountsPaths } from './generated/accounts';
import type { paths as AuthPaths } from './generated/auth';
import type { paths as BandsPaths } from './generated/bands';
import type { paths as CalendarPaths } from './generated/calendar';
import type { paths as CatalogPaths } from './generated/catalog';
import type { paths as CheckPaths } from './generated/check';
import type { paths as ImportPaths } from './generated/import';
import type { paths as PlacesPaths } from './generated/places';
import type { paths as SyncPaths } from './generated/sync';
import type { Problem, ProblemCode } from './types';

/**
 * Calls the app's API, typed by its OpenAPI specs (src/api/generated, from every module's
 * src/main/openapi): JSON both ways, the session cookie, and the CSRF token on every change.
 * A refusal or failure throws an {@link ApiError}; a 401 tells the page the session is gone.
 */

/** What the API said no with; `unanswered` when it gave no ProblemResponse (a proxy's error page, a lost session). */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: ProblemCode | 'unanswered',
    readonly args: Record<string, string>,
    message: string,
  ) {
    super(message);
  }
}

/** Told when the API says the session is gone (the page then shows the sign-in). */
let onUnauthorized: () => void = () => {};
export function whenUnauthorized(listener: () => void) {
  onUnauthorized = listener;
}

function cookie(name: string): string | null {
  const found = document.cookie.split('; ').find((c) => c.startsWith(name + '='));
  return found ? decodeURIComponent(found.substring(name.length + 1)) : null;
}

async function csrfToken(fresh = false): Promise<string> {
  const known = fresh ? null : cookie('XSRF-TOKEN');
  if (known) return known;
  const response = await fetch('/api/auth/csrf', { credentials: 'same-origin' });
  const body = (await response.json()) as { token: string };
  return cookie('XSRF-TOKEN') ?? body.token;
}

async function problem(response: Response): Promise<ApiError> {
  try {
    const p = (await response.json()) as Partial<Problem>;
    return new ApiError(response.status, p.code ?? 'unanswered', p.args ?? {}, p.message ?? response.statusText);
  } catch {
    return new ApiError(response.status, 'unanswered', {}, response.statusText);
  }
}

/** The client's fetch: adds the CSRF token to changes (a stale one — e.g. after signing in — is renewed once). */
async function apiFetch(request: Request, retried = false): Promise<Response> {
  const changes = request.method !== 'GET';
  const again = changes && !retried ? request.clone() : null;
  if (changes) request.headers.set('X-XSRF-TOKEN', await csrfToken(retried));
  const response = await fetch(request);
  // Only a CSRF refusal: Spring answers 403 with no problem body for a stale token, while the
  // app's own refusals are 409/404/400/429. Replaying ANY 403 would mean that the day an
  // endpoint answers 403 for "you may not", the page would quietly do the change twice.
  if (response.status === 403 && again && !response.headers.get('content-type')?.includes('json')) {
    return apiFetch(again, true);
  }
  if (response.status === 401) {
    if (!new URL(request.url).pathname.startsWith('/api/auth/')) onUnauthorized();
    throw await problem(response);
  }
  if (!response.ok) throw await problem(response);
  return response;
}

type Paths = AuthPaths & BandsPaths & AccountsPaths & CatalogPaths & PlacesPaths & SyncPaths & CalendarPaths & CheckPaths & ImportPaths;

export const api = createClient<Paths>({
  baseUrl: window.location.origin,
  credentials: 'same-origin',
  fetch: (request) => apiFetch(request),
});

/**
 * The answer's data; errors were thrown already. ({@code undefined} where the API answers
 * nothing, e.g. 204.)
 */
export async function data<T>(call: Promise<{ data?: T }>): Promise<T> {
  return (await call).data as T;
}
