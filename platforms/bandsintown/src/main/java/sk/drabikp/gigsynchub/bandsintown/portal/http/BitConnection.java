package sk.drabikp.gigsynchub.bandsintown.portal.http;

import sk.drabikp.gigsynchub.bandsintown.BitPortalException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

/**
 * What a band's one browser sign-in to the artist portal leaves the server to work with: the refresh token
 * the portal kept. That alone is enough — the manager comes back from the portal's own sign-in and the
 * artist from the list of what that manager looks after ({@link BitSignIn#artists()}) — so the band pastes
 * one value and never goes looking for a number. The two ids may still be given (they are kept with the
 * account once worked out, and an older paste carried them), and are then believed as they stand.
 *
 * <p>The fingerprint is ours, made up when the band connects and kept with it, so the portal records this
 * server's sessions as one device.
 *
 * <p>The band pastes this as it comes out of the portal's own page ({@link #read}); {@link #toString} never
 * shows the token.
 */
record BitConnection(String refreshToken, String managerId, String actorId, String fingerprint) {

    static final String FIELD = "connection";

    /**
     * The connection a band pasted, with a fingerprint made up for it: the refresh token on its own, or
     * the whole line an older paste gave. Refused when it holds no token ({@link BitPortalException}
     * needing the user — only they can paste it again).
     */
    static BitConnection read(String pasted) throws BitPortalException {
        String text = unquoted(pasted == null ? "" : pasted.strip());
        BitConnection connection = text.startsWith("{") ? fromJson(text)
                : new BitConnection(text, "", "", newFingerprint());
        if (connection.refreshToken().isBlank()) {
            throw BitPortalException.needsUser("That doesn't hold a Bandsintown sign-in — copy what the button on "
                    + "the portal's page gave you, all of it.");
        }
        return connection;
    }

    private static BitConnection fromJson(String text) throws BitPortalException {
        JsonNode json;
        try {
            json = JsonMapper.builder().build().readTree(text);
        } catch (JacksonException _) {
            throw BitPortalException.needsUser("That isn't what Bandsintown's page gave — copy the whole of it, "
                    + "braces and all.");
        }
        return new BitConnection(json.path("refreshToken").asString(""), json.path("managerId").asString(""),
                json.path("actorId").asString(""), newFingerprint());
    }

    /** The quotes a browser's console puts round a string it prints, when the band copied those too. */
    private static String unquoted(String text) {
        boolean quoted = text.length() > 1
                && ((text.startsWith("\"") && text.endsWith("\"")) || (text.startsWith("'") && text.endsWith("'")));
        return quoted ? text.substring(1, text.length() - 1).strip() : text;
    }

    private static String newFingerprint() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    @Override
    public String toString() {
        return "BitConnection[managerId=" + managerId + ", actorId=" + actorId + ", fingerprint=" + fingerprint
                + ", refreshToken=" + (refreshToken.isBlank() ? "" : "***") + "]";
    }
}
