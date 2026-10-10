package sk.drabikp.bzscraper.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.accounts.application.port.out.CurrentLogin;

import java.util.Optional;

/** Who Rauthy signed in on this request ({@link CurrentLogin}): the ID token's subject, e-mail and name. */
@Component
class OidcCurrentLogin implements CurrentLogin {

    @Override
    public Optional<Login> current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof OidcUser user)) {
            return Optional.empty();
        }
        String name = user.getFullName() != null ? user.getFullName() : user.getPreferredUsername();
        return Optional.of(new Login(user.getSubject(), user.getEmail(), name));
    }
}
