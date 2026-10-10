/**
 * Signing in is Rauthy's: the browser goes there (a page load, not an API call) and comes back to
 * the page's start; the address the user was at is kept for that moment (this tab only).
 */
const RETURN_TO = 'bzscraper.returnTo';

export function signIn() {
  try {
    sessionStorage.setItem(RETURN_TO, window.location.pathname + window.location.search);
  } catch {
    // no storage: the page's start then
  }
  window.location.assign('/oauth2/authorization/rauthy');
}

/** Where the user was before signing in — once; null when nowhere (or it was the start). */
export function takeReturnPath(): string | null {
  try {
    const path = sessionStorage.getItem(RETURN_TO);
    sessionStorage.removeItem(RETURN_TO);
    return path && path !== '/' && !path.startsWith('/?') ? path : null;
  } catch {
    return null;
  }
}

/** Rauthy sent the user back without a sign-in (`/?signin=failed`). */
export function signInFailed(): boolean {
  return new URLSearchParams(window.location.search).get('signin') === 'failed';
}
