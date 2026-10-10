package sk.drabikp.bzscraper.bandsintown.portal.http;

import sk.drabikp.bzscraper.bandsintown.BitPortalException;
import sk.drabikp.bzscraper.bandsintown.portal.PortalReply;

import java.util.Map;

/**
 * The artist portal's own API, as its pages call it, for one band's session: the requests, and the
 * addresses the band's events live at. {@link HttpBitSession} works only through this, so what it does
 * is tested without signing in to anything.
 */
interface BitApi {

    /** The manager whose session it is, as the addresses spell it. */
    String managerId();

    /** The artist, as the addresses spell it. */
    String actorId();

    /**
     * Where this artist's events live. The whole API hangs off the manager and the artist together —
     * the artist alone is not enough, and the list of an account's artists answers nothing for an
     * account like this one, so both come from the band's account.
     */
    default String actorPath(String suffix) {
        return "/api/managers/" + managerId() + "/managed-actors/" + actorId() + "/" + suffix;
    }

    /** What hangs off the manager alone — all there is before the artist is known. */
    default String managerPath(String suffix) {
        return "/api/managers/" + managerId() + "/" + suffix;
    }

    PortalReply get(String path) throws BitPortalException;

    /**
     * Sets an event's state — which is all the portal's own pages use a PATCH for (publish, move to
     * draft, delete); {@code body} is written as the request's JSON.
     */
    PortalReply patch(String path, Map<String, ?> body) throws BitPortalException;

    /**
     * Replaces something whole, which is how the portal's own edit form changes an event's fields: it
     * reads the event, lays the form's values over it and puts all of it back.
     */
    PortalReply put(String path, Map<String, ?> body) throws BitPortalException;

    /** A spreadsheet as the Bulk Upload sends it. */
    PortalReply multipartPatch(String path, String csv) throws BitPortalException;
}
