package sk.drabikp.gigsynchub.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;

/**
 * How long a sign-in lasts, and how soon the app notices one that Rauthy has ended.
 *
 * <p>Two different things, which is why both are here. {@code server.servlet.session.timeout} is an
 * IDLE timeout: a session that is used goes on for ever, so a browser that polls — the page does,
 * every live-update reconnect — holds a sign-in open indefinitely. And nothing in the app ever asked
 * Rauthy about it again: who may sign in is Rauthy's to say (its group for the app's client), but
 * that was said ONCE, at the sign-in, and taking a person out of the group or disabling them there
 * left every session they already had working.
 *
 * <p>So: {@link AuthProperties#sessionMaxAge()} (12 h) is an absolute cap counted from when the
 * session was MADE, whatever it has been doing since; and every
 * {@link AuthProperties#revalidateEvery()} (5 min) the first request to arrive asks Rauthy whether
 * the sign-in behind it still stands. Either answer that it does not ends the session at once: the
 * API answers 401 and the page shows the sign-in (Rauthy's own session, if it is still good, signs
 * the user back in without a password — which is why a cap that short costs a working user a click
 * rather than a sign-in).
 *
 * <p><b>What the asking can and cannot see.</b> The only credential the app holds for Rauthy is the
 * access token the sign-in left, and that client's tokens last half an hour
 * ({@code access_token_lifetime} in {@code docker/make-secrets.sh}); the app asks for no refresh
 * token, so once that half hour is up there is nothing left to ask WITH. The check is therefore
 * sharp for the first half hour of a session and the cap carries the rest — which is the honest
 * shape of it, and the reason the cap is not optional. Giving the client the refresh-token flow
 * would extend the asking to the whole session; it would also mean a long-lived credential in the
 * session, and re-bootstrapping Rauthy, so it is deliberately not done here.
 *
 * <p>Rauthy being unreachable is {@link Verdict#CANT_TELL} and keeps the session: an outage of the
 * sign-in must not sign everyone out of the app. The attempt still counts, so one unreachable
 * Rauthy costs one call per session per interval, not one per request.
 */
class SessionLife extends OncePerRequestFilter {

    /** What Rauthy says about a sign-in the app still holds. */
    enum Verdict {
        STILL_GOOD, OVER, CANT_TELL
    }

    /** Asking Rauthy, over the address inside the network ({@link RauthyStillSignedIn}). */
    interface Check {
        Verdict ask(String accessToken);
    }

    private static final String CHECKED_AT = SessionLife.class.getName() + ".checkedAt";

    private final AuthProperties auth;
    private final OAuth2AuthorizedClientRepository clients;
    private final Check check;
    private final Clock clock;

    SessionLife(AuthProperties auth, OAuth2AuthorizedClientRepository clients, Check check, Clock clock) {
        this.auth = auth;
        this.clients = clients;
        this.check = check;
        this.clock = clock;
    }

    /**
     * The API, less signing out — which must work with whatever session the browser still has, or a
     * user whose session we have just ended cannot be told where Rauthy ends its own.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.startsWith("/api/") || uri.equals("/api/auth/logout");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        Authentication who = SecurityContextHolder.getContext().getAuthentication();
        if (session == null || who == null || !who.isAuthenticated() || who instanceof AnonymousAuthenticationToken) {
            chain.doFilter(request, response);
            return;
        }
        long now = clock.millis();
        if (now - session.getCreationTime() >= auth.sessionMaxAge().toMillis()) {
            end(session, response);
            return;
        }
        long asked = session.getAttribute(CHECKED_AT) instanceof Long when ? when : session.getCreationTime();
        if (now - asked >= auth.revalidateEvery().toMillis()) {
            session.setAttribute(CHECKED_AT, now);        // before asking: one call per interval, answer or none
            if (stillSignedIn(request, who) == Verdict.OVER) {
                end(session, response);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private Verdict stillSignedIn(HttpServletRequest request, Authentication who) {
        OAuth2AuthorizedClient client =
                clients.loadAuthorizedClient(SecurityConfiguration.REGISTRATION, who, request);
        if (client == null) {
            return Verdict.CANT_TELL;
        }
        OAuth2AccessToken token = client.getAccessToken();
        if (token == null || token.getExpiresAt() == null || !token.getExpiresAt().isAfter(clock.instant())) {
            return Verdict.CANT_TELL;                     // nothing left to ask with
        }
        return check.ask(token.getTokenValue());
    }

    /** The session is over: nothing of it is left, and the page is told the way it is told anything. */
    private static void end(HttpSession session, HttpServletResponse response) {
        session.invalidate();
        SecurityContextHolder.clearContext();
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
    }
}
