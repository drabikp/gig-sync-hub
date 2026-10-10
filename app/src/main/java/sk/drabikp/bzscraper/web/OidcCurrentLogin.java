package sk.drabikp.bzscraper.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.accounts.application.port.out.CurrentLogin;

import java.util.Optional;

/**
 * Who Rauthy signed in on this request ({@link CurrentLogin}): the ID token's subject, e-mail (unless Rauthy
 * says it isn't verified) and name.
 */
@Component
class OidcCurrentLogin implements CurrentLogin {

    @Override
    public Optional<Login> current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof OidcUser user)) {
            return Optional.empty();
        }
        String name = user.getFullName() != null ? user.getFullName() : user.getPreferredUsername();
        // an address the sign-in says it hasn't verified is no address (invitations are matched on it)
        String email = Boolean.FALSE.equals(user.getEmailVerified()) ? null : user.getEmail();
        return Optional.of(new Login(user.getSubject(), email, name));
    }
}
