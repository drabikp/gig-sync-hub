package sk.drabikp.gigsynchub.bandsintown.portal.http;

import sk.drabikp.gigsynchub.bandsintown.BitPortalException;
import sk.drabikp.gigsynchub.portal.PlatformEgress;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * A band's way into the artist portal without a browser, from what its one browser sign-in left behind.
 * The portal's sign-in allows only the browser's own exchange, so the band did that once and the server
 * keeps the refresh token; from it this works out a session whenever one is needed:
 * <ol>
 *   <li>the refresh token is exchanged for an access token (the one exchange a server may do, and the
 *       only one the portal's identity provider allows us);</li>
 *   <li>the access token is handed to the portal's own sign-in, which answers the session the API wants
 *       — recorded against the {@link #fingerprint()} we made up for this band, nothing of a browser's.</li>
 * </ol>
 * A session is kept until shortly before it goes stale, so a run of work signs in once. When the refresh
 * token itself is over — the band signed out, or the window the portal allows has passed — only the band
 * can renew it, in their browser: {@code NEEDS_USER}, never tried again.
 */
final class BitSignIn implements BitCredentials {

    /** How long before an access token's own expiry a fresh session is worked out. */
    private static final Duration EARLY = Duration.ofMinutes(5);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    /** Which of the portal's sign-ins we are using, and as what kind of account. */
    private static final String PROVIDER = "COGNITO";
    private static final String DOMAIN = "artists";

    private final String baseUrl;
    private final BitConnection connection;
    private final Clock clock;
    private final JsonMapper json = JsonMapper.builder().build();
    private final HttpClient http;
    private String session;
    private Instant staleAt = Instant.EPOCH;
    /** The manager the portal's own sign-in named, when the band's connection didn't carry one. */
    private String managerFromPortal = "";

    /** Straight out of this machine — a test, and anything that has no properties to hand. */
    BitSignIn(String baseUrl, BitConnection connection, Clock clock) {
        this(baseUrl, connection, clock, PlatformEgress.DIRECT);
    }

    BitSignIn(String baseUrl, BitConnection connection, Clock clock, PlatformEgress egress) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.connection = connection;
        this.clock = clock;
        this.http = egress.on(HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10)))
                .build();
    }

    @Override
    public synchronized String sessionToken() throws BitPortalException {
        if (session != null && clock.instant().isBefore(staleAt)) {
            return session;
        }
        session = null;
        Pool pool = pool();
        String access = refreshed(pool);
        session = signedIn(access);
        return session;
    }

    @Override
    public String fingerprint() {
        return connection.fingerprint();
    }

    @Override
    public String managerId() {
        return connection.managerId().isBlank() ? managerFromPortal : connection.managerId();
    }

    @Override
    public String actorId() {
        return connection.actorId();
    }

    /** Which identity provider the portal signs in with — its pages are told, so we ask the same way. */
    private Pool pool() throws BitPortalException {
        JsonNode payload = answer(HttpRequest.newBuilder(URI.create(baseUrl + "/api/client/cognito/config")).GET(),
                "the portal's sign-in settings").path("payload");
        String poolId = payload.path("userPoolId").asString("");
        String clientId = payload.path("clientId").asString("");
        if (poolId.isBlank() || clientId.isBlank() || !poolId.contains("_")) {
            throw BitPortalException.temporary("Bandsintown didn't say how it signs in (its settings have no pool).");
        }
        return new Pool(poolId.substring(0, poolId.indexOf('_')), clientId);
    }

    /** The access token the kept refresh token is worth now. */
    private String refreshed(Pool pool) throws BitPortalException {
        JsonNode answer = answer(HttpRequest.newBuilder(URI.create("https://cognito-idp." + pool.region()
                        + ".amazonaws.com/"))
                .header("X-Amz-Target", "AWSCognitoIdentityProviderService.InitiateAuth")
                .header("Content-Type", "application/x-amz-json-1.1")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of(
                        "AuthFlow", "REFRESH_TOKEN_AUTH",
                        "ClientId", pool.clientId(),
                        "AuthParameters", Map.of("REFRESH_TOKEN", connection.refreshToken()))))),
                "renewing the band's sign-in");
        JsonNode result = answer.path("AuthenticationResult");
        String access = result.path("AccessToken").asString("");
        if (access.isBlank()) {
            throw BitPortalException.needsUser("Bandsintown's sign-in for this band is over — sign in there again "
                    + "and connect it in Settings.");
        }
        long seconds = result.path("ExpiresIn").asLong(0);
        staleAt = clock.instant().plusSeconds(Math.max(0, seconds)).minus(EARLY);
        return access;
    }

    /** The portal's own sign-in: the session its API wants, against this band's fingerprint. */
    private String signedIn(String accessToken) throws BitPortalException {
        JsonNode answer = answer(HttpRequest.newBuilder(URI.create(baseUrl + "/api/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(mapOf(accessToken)))),
                "signing in to the portal");
        String token = answer.path("payload").path("token").asString("");
        managerFromPortal = answer.path("payload").path("managerId").asString("");
        if (token.isBlank()) {
            if (answer.path("payload").path("requiresVerification").asBoolean(false)) {
                throw BitPortalException.needsUser("Bandsintown wants this band's sign-in verified — sign in there "
                        + "in a browser, then connect it in Settings again.");
            }
            throw BitPortalException.temporary("Bandsintown's sign-in gave no session.");
        }
        return token;
    }

    /** What the portal's sign-in is sent; {@code manager_id} is null because the account names it back. */
    private Map<String, Object> mapOf(String accessToken) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("access_token", accessToken);
        body.put("provider", PROVIDER);
        body.put("manager_id", null);
        body.put("short_session", false);
        body.put("domain", DOMAIN);
        body.put("fingerprint", connection.fingerprint());
        return body;
    }

    private JsonNode answer(HttpRequest.Builder request, String what) throws BitPortalException {
        HttpResponse<String> response;
        try {
            response = http.send(request.timeout(TIMEOUT).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            throw BitPortalException.busy("Interrupted while " + what + " — again later.");
        } catch (IOException e) {
            throw BitPortalException.temporary("Could not reach Bandsintown while " + what + ": "
                    + e.getClass().getSimpleName(), e);
        }
        if (response.statusCode() == 400 || response.statusCode() == 401 || response.statusCode() == 403) {
            // the refresh token is refused, not merely unlucky: only the band can renew it
            throw BitPortalException.needsUser("Bandsintown would not renew this band's sign-in — sign in there "
                    + "again and connect it in Settings.");
        }
        if (response.statusCode() != 200) {
            throw BitPortalException.temporary("Bandsintown answered " + response.statusCode() + " while " + what
                    + ".");
        }
        try {
            return json.readTree(response.body());
        } catch (JacksonException e) {
            throw BitPortalException.temporary("Bandsintown's answer while " + what + " was no JSON.", e);
        }
    }

    /** The identity provider a portal signs in with. */
    private record Pool(String region, String clientId) {
    }
}
