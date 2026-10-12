package sk.drabikp.gigsynchub.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;
import sk.drabikp.gigsynchub.web.api.LogoutResponse;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

/**
 * After the app's session ended: where the page sends the browser to end the Rauthy session too
 * ({@link LogoutResponse}) — Rauthy's end-session endpoint, naming the ID token and the page to
 * come back to. An answer, not a redirect: the page signs out with an API call.
 */
class RauthySignOut implements LogoutSuccessHandler {

    private final ClientRegistration rauthy;
    private final JsonMapper json;

    RauthySignOut(ClientRegistration rauthy, JsonMapper json) {
        this.rauthy = rauthy;
        this.json = json;
    }

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response,
                                Authentication authentication) throws IOException {
        String page = ServletUriComponentsBuilder.fromContextPath(request).path("/").toUriString();
        UriComponentsBuilder endSession = UriComponentsBuilder.fromUriString(
                        (String) rauthy.getProviderDetails().getConfigurationMetadata().get("end_session_endpoint"))
                .queryParam("post_logout_redirect_uri", page);
        if (authentication != null && authentication.getPrincipal() instanceof OidcUser user) {
            endSession.queryParam("id_token_hint", user.getIdToken().getTokenValue());
        }
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(response.getOutputStream(), new LogoutResponse(endSession.encode().toUriString()));
    }
}
