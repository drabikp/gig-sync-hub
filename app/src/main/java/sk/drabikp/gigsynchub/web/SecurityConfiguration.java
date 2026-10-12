package sk.drabikp.gigsynchub.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.savedrequest.NullRequestCache;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.Map;

/**
 * Who may use the app: whoever signed in through Rauthy (OpenID Connect, the authorization code with
 * PKCE) into a session cookie — who may sign in is Rauthy's to say (today: its group for the app's
 * client); which bands they see is the app's (their memberships, {@link BandAccessInterceptor}). The
 * page sends the browser to {@code /oauth2/authorization/rauthy}; Rauthy sends it back to
 * {@code /login/oauth2/code/rauthy}, and then to the page. Everything under {@code /api} needs a
 * sign-in (401 when there is none — never a redirect, the page starts the sign-in itself); the page
 * itself and its files are public. CSRF protection as for a
 * single-page app: the token travels in the {@code XSRF-TOKEN} cookie and comes back in the
 * {@code X-XSRF-TOKEN} header.
 *
 * <p>Rauthy is reached at two addresses: the browser goes to the issuer (through the proxy), the
 * app fetches tokens, keys and the user's details inside the containers' network
 * ({@link AuthProperties#internalUrl()}); the tokens name the issuer, which is checked.
 */
@Configuration
class SecurityConfiguration {

    static final String REGISTRATION = "rauthy";

    @Bean
    ClientRegistrationRepository clientRegistrations(AuthProperties auth) {
        ClientRegistration rauthy = ClientRegistration.withRegistrationId(REGISTRATION)
                .clientName("Rauthy")
                .clientId(auth.clientId())
                .clientSecret(auth.clientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .clientSettings(ClientRegistration.ClientSettings.builder().requireProofKey(true).build())
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "email", "profile", "groups")
                .issuerUri(auth.issuer())
                .authorizationUri(auth.issuer() + "oidc/authorize")
                .tokenUri(auth.internalUrl() + "oidc/token")
                .jwkSetUri(auth.internalUrl() + "oidc/certs")
                .userInfoUri(auth.internalUrl() + "oidc/userinfo")
                .userNameAttributeName("email")
                .providerConfigurationMetadata(Map.of("end_session_endpoint", auth.issuer() + "oidc/logout"))
                .build();
        return new InMemoryClientRegistrationRepository(rauthy);
    }

    @Bean
    SecurityFilterChain api(HttpSecurity http, ClientRegistrationRepository registrations, JsonMapper json,
                            LimitsProperties limits) throws Exception {
        http.authorizeHttpRequests(requests -> requests
                        // the actuator answers on the management port only (this machine, the operator)
                        .requestMatchers("/api/auth/**", "/actuator/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .oauth2Login(login -> login
                        .defaultSuccessUrl("/", true)
                        .failureUrl("/?signin=failed"))
                // an API call without a session is a 401, not a request to return to after signing in
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .csrf(csrf -> csrf.spa())
                .logout(logout -> logout.logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(new RauthySignOut(registrations.findByRegistrationId(REGISTRATION), json)))
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                // after the session is known (counted per user), before anything is decided or done
                .addFilterBefore(new RateLimits(limits, json, Clock.systemUTC()), AuthorizationFilter.class);
        return http.build();
    }
}
