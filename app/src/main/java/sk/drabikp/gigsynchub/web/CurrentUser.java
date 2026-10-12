package sk.drabikp.gigsynchub.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Component;
import sk.drabikp.gigsynchub.accounts.application.port.in.UsersUseCase;
import sk.drabikp.gigsynchub.web.api.CsrfTokenResponse;
import sk.drabikp.gigsynchub.web.api.MeResponse;

import java.util.Optional;

/** The current request's sign-in, as the page asks for it: the CSRF token, and who is signed in. */
@Component
class CurrentUser {

    private final HttpServletRequest request;
    private final UsersUseCase users;

    CurrentUser(HttpServletRequest request, UsersUseCase users) {
        this.request = request;
        this.users = users;
    }

    /** The request's CSRF token; reading it also sets its cookie. */
    CsrfTokenResponse csrfToken() {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return new CsrfTokenResponse(token.getHeaderName(), token.getToken());
    }

    /** Who is signed in (made a user of the app at the first sign-in); empty when nobody is. */
    Optional<MeResponse> me() {
        return users.current().map(user -> new MeResponse(user.email() != null ? user.email() : user.shownAs(),
                user.shownAs()));
    }
}
