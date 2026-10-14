package sk.drabikp.gigsynchub.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@code gigsynchub.auth.*}: the sign-in through Rauthy (OpenID Connect).
 *
 * @param issuer          Rauthy as the browser reaches it and as its tokens name it
 *                        ({@code https://<domain>/auth/v1/})
 * @param internalUrl     Rauthy as the app reaches it (tokens, keys, user info): inside the
 *                        containers' network ({@code http://rauthy:8080/auth/v1/}); empty = the issuer
 * @param clientId        the app's client in Rauthy
 * @param clientSecret    its secret — NEVER in source (in Docker the secret {@code auth_client_secret})
 * @param sessionMaxAge   how long a session may live from when it was made, however busy it has been
 *                        (12 h); {@code server.servlet.session.timeout} is the IDLE one beside it
 * @param revalidateEvery how often the app asks Rauthy whether a sign-in it holds still stands
 *                        (5 min) — see {@link SessionLife} for what that can and cannot see
 * @param groupPrefix     the group a person must still be in to use the app: Rauthy's
 *                        {@code restrict_group_prefix} on this client, which is the client's own id
 */
@ConfigurationProperties("gigsynchub.auth")
public record AuthProperties(String issuer, String internalUrl, String clientId, String clientSecret,
                             Duration sessionMaxAge, Duration revalidateEvery, String groupPrefix) {

    public AuthProperties {
        issuer = withSlash(issuer == null || issuer.isBlank() ? "https://localhost/auth/v1/" : issuer.strip());
        internalUrl = internalUrl == null || internalUrl.isBlank() ? issuer : withSlash(internalUrl.strip());
        clientId = clientId == null || clientId.isBlank() ? "gigsynchub" : clientId.strip();
        clientSecret = clientSecret == null ? "" : clientSecret.strip();
        sessionMaxAge = sessionMaxAge == null || sessionMaxAge.isNegative() || sessionMaxAge.isZero()
                ? Duration.ofHours(12) : sessionMaxAge;
        revalidateEvery = revalidateEvery == null || revalidateEvery.isNegative()
                ? Duration.ofMinutes(5) : revalidateEvery;
        groupPrefix = groupPrefix == null || groupPrefix.isBlank() ? clientId : groupPrefix.strip();
    }

    private static String withSlash(String url) {
        return url.endsWith("/") ? url : url + "/";
    }

    @Override
    public String toString() {
        return "AuthProperties[issuer=" + issuer + ", internalUrl=" + internalUrl + ", clientId=" + clientId
                + ", clientSecret=" + (clientSecret.isEmpty() ? "" : "***") + ", sessionMaxAge=" + sessionMaxAge
                + ", revalidateEvery=" + revalidateEvery + ", groupPrefix=" + groupPrefix + "]";
    }
}
