package sk.drabikp.bzscraper.bandsintown.portal.http;

import sk.drabikp.bzscraper.bandsintown.BitPortalException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

/**
 * What a band's one browser sign-in to the artist portal leaves the server to work with: the refresh token
 * the portal kept, and the two numbers its addresses are built from — the manager and the artist (its
 * "managed actor"), which an account like this one cannot be asked for afterwards, so they are collected
 * at the same time. The fingerprint is ours, made up when the band connects and kept with it, so the
 * portal records this server's sessions as one device.
 *
 * <p>The band pastes this as it comes out of the portal's own page ({@link #read}); {@link #toString} never
 * shows the token.
 */
record BitConnection(String refreshToken, String managerId, String actorId, String fingerprint) {

    static final String FIELD = "connection";

    /**
     * The connection a band pasted, as the snippet in Settings prints it, with a fingerprint made up for
     * it. Refused when it isn't that ({@link BitPortalException} needing the user — only they can paste
     * it again).
     */
    static BitConnection read(String pasted) throws BitPortalException {
        JsonNode json;
        try {
            json = JsonMapper.builder().build().readTree(pasted == null ? "" : pasted.strip());
        } catch (JacksonException e) {
            throw BitPortalException.needsUser("That isn't what Bandsintown's page printed — copy the whole line "
                    + "it gave, braces and all.");
        }
        BitConnection connection = new BitConnection(json.path("refreshToken").asString(""),
                json.path("managerId").asString(""), json.path("actorId").asString(""),
                UUID.randomUUID().toString().replace("-", ""));
        if (connection.refreshToken().isBlank() || connection.managerId().isBlank()
                || connection.actorId().isBlank()) {
            throw BitPortalException.needsUser("That line is missing part of the sign-in (it needs the refresh "
                    + "token, the manager and the artist) — run the snippet again on the portal's events page.");
        }
        return connection;
    }

    @Override
    public String toString() {
        return "BitConnection[managerId=" + managerId + ", actorId=" + actorId + ", fingerprint=" + fingerprint
                + ", refreshToken=" + (refreshToken.isBlank() ? "" : "***") + "]";
    }
}
