package sk.drabikp.gigsynchub.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import sk.drabikp.gigsynchub.web.api.AuthApi;
import sk.drabikp.gigsynchub.web.api.CsrfTokenResponse;
import sk.drabikp.gigsynchub.web.api.MeResponse;

/**
 * Serves the page's questions about signing in (auth.yaml); signing in is Rauthy's and the security
 * filter's, signing out the filter's ({@link SecurityConfiguration}).
 */
@RestController
class AuthController implements AuthApi {

    private final CurrentUser currentUser;

    AuthController(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    @Override
    public ResponseEntity<CsrfTokenResponse> csrfToken() {
        return ResponseEntity.ok(currentUser.csrfToken());
    }

    @Override
    public ResponseEntity<MeResponse> currentUser() {
        return currentUser.me().map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }
}
