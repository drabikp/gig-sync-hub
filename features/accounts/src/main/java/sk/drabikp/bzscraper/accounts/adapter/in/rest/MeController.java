package sk.drabikp.bzscraper.accounts.adapter.in.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import sk.drabikp.bzscraper.accounts.adapter.in.rest.api.MeApi;
import sk.drabikp.bzscraper.accounts.application.port.in.UsersUseCase;

/** Serves the signed-in user's own account (accounts.yaml). */
@RestController
class MeController implements MeApi {

    private final UsersUseCase users;

    MeController(UsersUseCase users) {
        this.users = users;
    }

    @Override
    public ResponseEntity<Void> deleteMyAccount() {
        users.deleteMe(users.current().orElseThrow().id());
        return ResponseEntity.noContent().build();
    }
}
