package sk.drabikp.bzscraper.accounts.adapter.in.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import sk.drabikp.bzscraper.accounts.adapter.in.rest.api.PlatformAccountRequest;
import sk.drabikp.bzscraper.accounts.adapter.in.rest.api.PlatformAccountResponse;
import sk.drabikp.bzscraper.accounts.adapter.in.rest.api.PlatformAccountsApi;
import sk.drabikp.bzscraper.accounts.application.port.in.PlatformAccountsUseCase;
import sk.drabikp.bzscraper.accounts.application.port.in.UsersUseCase;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.platform.Platforms;

import java.util.List;
import java.util.UUID;

/** Serves a band's platform accounts (accounts.yaml); every call is the signed-in user's. */
@RestController
class PlatformAccountsController implements PlatformAccountsApi {

    private final UsersUseCase users;
    private final PlatformAccountsUseCase accounts;
    private final Platforms platforms;
    private final AccountsMapper mapper;

    PlatformAccountsController(UsersUseCase users, PlatformAccountsUseCase accounts, Platforms platforms,
                               AccountsMapper mapper) {
        this.users = users;
        this.accounts = accounts;
        this.platforms = platforms;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<List<PlatformAccountResponse>> platformAccounts(UUID bandId) {
        return ResponseEntity.ok(mapper.toAccountResponses(
                accounts.accounts(users.current().orElseThrow().id(), new BandId(bandId))));
    }

    @Override
    public ResponseEntity<PlatformAccountResponse> connectPlatformAccount(UUID bandId, String platform,
                                                                          PlatformAccountRequest request) {
        return ResponseEntity.ok(mapper.toResponse(accounts.connect(users.current().orElseThrow().id(),
                new BandId(bandId), platforms.get(platform), request.getSettings(), request.getSecrets())));
    }

    @Override
    public ResponseEntity<Void> disconnectPlatformAccount(UUID bandId, String platform) {
        accounts.disconnect(users.current().orElseThrow().id(), new BandId(bandId), platforms.get(platform));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<PlatformAccountResponse> checkPlatformAccount(UUID bandId, String platform) {
        return ResponseEntity.ok(mapper.toResponse(accounts.check(users.current().orElseThrow().id(),
                new BandId(bandId), platforms.get(platform))));
    }
}
