package sk.drabikp.gigsynchub.web;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A sign-in does not last for ever, and the app finds out when Rauthy has ended one.
 *
 * <p>Both of these are about TIME passing, which is why they are here and not in
 * {@code ApiSecurityTest}: a session's age is read from the session itself and a whole context's
 * clock cannot be wound forward around it. The filter takes its clock, so this test can.
 */
class SessionLifeTest {

    private static final Duration CAP = Duration.ofHours(12);
    private static final Duration EVERY = Duration.ofMinutes(5);

    private Instant now = Instant.EPOCH;
    private final List<String> asked = new ArrayList<>();
    private SessionLife.Verdict rauthySays = SessionLife.Verdict.STILL_GOOD;

    private final AuthProperties auth =
            new AuthProperties("https://auth.test/auth/v1/", "", "gigsynchub", "secret", CAP, EVERY, null);
    private final OAuth2AuthorizedClientRepository clients = new HttpSessionOAuth2AuthorizedClientRepository();
    private final Authentication who = new TestingAuthenticationToken("owner@band.test", "n/a", "ROLE_USER");

    private final SessionLife filter = new SessionLife(auth, clients, token -> {
        asked.add(token);
        return rauthySays;
    }, new Clock() {
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    });

    private MockHttpSession session;

    @BeforeEach
    void signedIn() {
        session = new MockHttpSession();
        // a session's creation time is the machine's, not this test's clock: start ours from it
        now = Instant.ofEpochMilli(session.getCreationTime());
        SecurityContextHolder.getContext().setAuthentication(who);
    }

    @AfterEach
    void signedOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("a session that is still within its life goes through")
    void a_session_that_is_still_within_its_life_goes_through() throws Exception {
        now = now.plus(Duration.ofHours(11));
        assertThat(call()).isEqualTo(200);
        assertThat(session.isInvalid()).isFalse();
    }

    @Test
    @DisplayName("a session past its cap is over, however busy it has been")
    void a_session_past_its_cap_is_over_however_busy_it_has_been() throws Exception {
        for (int hour = 1; hour < 12; hour++) {           // used every hour: an idle timeout never fires
            now = now.plus(Duration.ofHours(1));
            assertThat(call()).isEqualTo(200);
        }
        now = now.plus(Duration.ofHours(1));
        assertThat(call()).isEqualTo(401);
        assertThat(session.isInvalid()).as("nothing of the session is left").isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Rauthy is asked once an interval, not once a request")
    void rauthy_is_asked_once_an_interval_not_once_a_request() throws Exception {
        withALiveToken();
        for (int i = 0; i < 20; i++) {
            now = now.plus(Duration.ofSeconds(30));
            assertThat(call()).isEqualTo(200);
        }
        assertThat(asked).as("ten minutes of requests, two intervals").hasSize(2);
    }

    @Test
    @DisplayName("a sign-in Rauthy has ended ends the session on the next ask")
    void a_sign_in_rauthy_has_ended_ends_the_session_on_the_next_ask() throws Exception {
        withALiveToken();
        rauthySays = SessionLife.Verdict.OVER;
        now = now.plus(Duration.ofMinutes(1));
        assertThat(call()).as("not yet asked").isEqualTo(200);
        now = now.plus(Duration.ofMinutes(5));
        assertThat(call()).isEqualTo(401);
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    @DisplayName("Rauthy being unreachable keeps the session")
    void rauthy_being_unreachable_keeps_the_session() throws Exception {
        withALiveToken();
        rauthySays = SessionLife.Verdict.CANT_TELL;
        now = now.plus(Duration.ofMinutes(6));
        assertThat(call()).isEqualTo(200);
        assertThat(session.isInvalid()).isFalse();
        now = now.plus(Duration.ofMinutes(1));
        assertThat(call()).isEqualTo(200);
        assertThat(asked).as("an unreachable Rauthy costs one call an interval, not one a request").hasSize(1);
    }

    @Test
    @DisplayName("a token too old to ask with is not an answer: the cap carries it")
    void a_token_too_old_to_ask_with_is_not_an_answer_the_cap_carries_it() throws Exception {
        withALiveToken();                                 // the sign-in's token lasts half an hour
        now = now.plus(Duration.ofHours(2));
        assertThat(call()).isEqualTo(200);
        assertThat(asked).as("there is nothing left to ask Rauthy with").isEmpty();
    }

    @Test
    @DisplayName("signing out is never refused by this")
    void signing_out_is_never_refused_by_this() throws Exception {
        now = now.plus(Duration.ofDays(3));
        assertThat(call("/api/auth/logout")).isEqualTo(200);
        assertThat(session.isInvalid()).isFalse();
    }

    /** The access token the sign-in left: this client's last half an hour (docker/make-secrets.sh). */
    private void withALiveToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        ClientRegistration rauthy = ClientRegistration.withRegistrationId(SecurityConfiguration.REGISTRATION)
                .clientId("gigsynchub")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://app.test/login/oauth2/code/rauthy")
                .authorizationUri("https://auth.test/auth/v1/oidc/authorize")
                .tokenUri("https://auth.test/auth/v1/oidc/token")
                .build();
        OAuth2AccessToken token = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "the-token",
                now, now.plus(Duration.ofMinutes(30)));
        clients.saveAuthorizedClient(new OAuth2AuthorizedClient(rauthy, who.getName(), token), who,
                request, new MockHttpServletResponse());
    }

    private int call() throws Exception {
        return call("/api/bands");
    }

    private int call(String uri) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setSession(session);
        HttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return chain.getRequest() == null ? response.getStatus() : 200;
    }
}
