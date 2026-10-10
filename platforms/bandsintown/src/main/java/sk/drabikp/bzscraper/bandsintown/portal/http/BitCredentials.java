package sk.drabikp.bzscraper.bandsintown.portal.http;

import sk.drabikp.bzscraper.bandsintown.BitPortalException;

/**
 * What one band's authenticated artist-portal session needs, as signing in works it out. The portal's
 * app client allows only the browser's sign-in, so a band signs in once in its own browser and the
 * server keeps what that left behind; from then on this is all the API needs.
 */
interface BitCredentials {

    /**
     * The session token for the band's next request — asked for again before every one, so a session
     * worked out afresh (the kept token refreshed) is used without opening anything anew.
     *
     * @throws BitPortalException needing the user when the band's sign-in is over: it is theirs to
     *                            renew in their browser, never something to try again with
     */
    String sessionToken() throws BitPortalException;

    /** The device the portal recorded this session against: ours, kept with the band's account. */
    String fingerprint();

    /** The manager whose session it is, as the portal's addresses spell it. */
    String managerId();

    /** The artist, as the portal's addresses spell it (its "managed actor"). */
    String actorId();
}
