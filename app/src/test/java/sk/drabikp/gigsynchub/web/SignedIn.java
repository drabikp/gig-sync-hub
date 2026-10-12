package sk.drabikp.gigsynchub.web;

import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;

/** Signed in through Rauthy, as Spring Security's OIDC test support makes it (the made-up Rauthy is never called). */
final class SignedIn {

    /** Band #1's owner (gigsynchub.accounts.first-owner-email of the test properties). */
    static final String OWNER = "owner@band.test";

    private SignedIn() {
    }

    static OidcLoginRequestPostProcessor as(String email) {
        return oidcLogin().idToken(token -> token.subject("rauthy-" + email).claim("email", email)
                .claim("name", email.substring(0, email.indexOf('@'))));
    }

    static OidcLoginRequestPostProcessor owner() {
        return as(OWNER);
    }
}
