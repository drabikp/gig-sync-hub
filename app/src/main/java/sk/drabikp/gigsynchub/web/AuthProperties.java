package sk.drabikp.gigsynchub.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code gigsynchub.auth.*}: the sign-in through Rauthy (OpenID Connect).
 *
 * @param issuer       Rauthy as the browser reaches it and as its tokens name it
 *                     ({@code https://<domain>/auth/v1/})
 * @param internalUrl  Rauthy as the app reaches it (tokens, keys, user info): inside the
 *                     containers' network ({@code http://rauthy:8080/auth/v1/}); empty = the issuer
 * @param clientId     the app's client in Rauthy
 * @param clientSecret its secret — NEVER in source (in Docker the secret {@code auth_client_secret})
 */
@ConfigurationProperties("gigsynchub.auth")
public record AuthProperties(String issuer, String internalUrl, String clientId, String clientSecret) {

    public AuthProperties {
        issuer = withSlash(issuer == null || issuer.isBlank() ? "https://localhost/auth/v1/" : issuer.strip());
        internalUrl = internalUrl == null || internalUrl.isBlank() ? issuer : withSlash(internalUrl.strip());
        clientId = clientId == null || clientId.isBlank() ? "gigsynchub" : clientId.strip();
        clientSecret = clientSecret == null ? "" : clientSecret.strip();
    }

    private static String withSlash(String url) {
        return url.endsWith("/") ? url : url + "/";
    }

    @Override
    public String toString() {
        return "AuthProperties[issuer=" + issuer + ", internalUrl=" + internalUrl + ", clientId=" + clientId
                + ", clientSecret=" + (clientSecret.isEmpty() ? "" : "***") + "]";
    }
}
