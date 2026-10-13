package sk.drabikp.gigsynchub.web;

import org.springframework.security.oauth2.client.registration.ClientRegistration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Asks Rauthy whether a sign-in the app still holds is still a sign-in: its user-info address
 * ({@code oidc/userinfo}) with the access token, over the address INSIDE the containers' network
 * ({@link AuthProperties#internalUrl()}), never through the proxy.
 *
 * <p>Refused (401 or 403) means Rauthy no longer knows this token: the person was disabled, deleted
 * or signed out there. Answered, but no longer in a group the app's client admits
 * ({@link AuthProperties#groupPrefix()} — Rauthy's {@code restrict_group_prefix}), means they are
 * no longer one of the people who may use this app. Both end the session. Anything else — Rauthy
 * down, a timeout, an answer that isn't JSON — is {@link SessionLife.Verdict#CANT_TELL}, because a
 * sign-in that cannot be asked about is not a sign-in that has ended.
 *
 * <p>Whether the groups come back here at all is Rauthy's business and its version's: the claim is
 * in the ID token for certain, in the user info only when that scope's claims are served there. So
 * an answer WITHOUT groups is taken at its word — it still proves the token is live — and one WITH
 * them is held to them. Nothing here guesses.
 *
 * <p>The timeouts are short on purpose: this sits in front of an API call a person is waiting for,
 * and an unreachable sign-in must cost that person a second, not a minute.
 */
class RauthyStillSignedIn implements SessionLife.Check {

    private static final Duration CONNECT = Duration.ofSeconds(2);
    private static final Duration ANSWER = Duration.ofSeconds(3);

    private final URI userInfo;
    private final String groupPrefix;
    private final JsonMapper json;
    private final HttpClient http;

    RauthyStillSignedIn(ClientRegistration rauthy, String groupPrefix, JsonMapper json) {
        this.userInfo = URI.create(rauthy.getProviderDetails().getUserInfoEndpoint().getUri());
        this.groupPrefix = groupPrefix;
        this.json = json;
        this.http = HttpClient.newBuilder().connectTimeout(CONNECT).build();
    }

    @Override
    public SessionLife.Verdict ask(String accessToken) {
        try {
            HttpResponse<String> answer = http.send(HttpRequest.newBuilder(userInfo)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/json")
                    .timeout(ANSWER)
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            if (answer.statusCode() == 401 || answer.statusCode() == 403) {
                return SessionLife.Verdict.OVER;
            }
            if (answer.statusCode() / 100 != 2) {
                return SessionLife.Verdict.CANT_TELL;
            }
            return stillOneOfUs(answer.body()) ? SessionLife.Verdict.STILL_GOOD : SessionLife.Verdict.OVER;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SessionLife.Verdict.CANT_TELL;
        } catch (IOException | RuntimeException e) {
            return SessionLife.Verdict.CANT_TELL;
        }
    }

    /** No groups in the answer: nothing to go on, and the live token is answer enough. */
    private boolean stillOneOfUs(String body) {
        JsonNode groups = json.readTree(body).path("groups");
        if (!groups.isArray()) {
            return true;
        }
        for (JsonNode group : groups) {
            if (group.asString("").startsWith(groupPrefix)) {
                return true;
            }
        }
        return false;
    }
}
