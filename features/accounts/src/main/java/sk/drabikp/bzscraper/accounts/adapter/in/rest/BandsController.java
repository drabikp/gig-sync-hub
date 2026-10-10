package sk.drabikp.bzscraper.accounts.adapter.in.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import sk.drabikp.bzscraper.accounts.adapter.in.rest.api.AuditEntryResponse;
import sk.drabikp.bzscraper.accounts.adapter.in.rest.api.BandRequest;
import sk.drabikp.bzscraper.accounts.adapter.in.rest.api.BandsApi;
import sk.drabikp.bzscraper.accounts.adapter.in.rest.api.MemberResponse;
import sk.drabikp.bzscraper.accounts.adapter.in.rest.api.MyBandResponse;
import sk.drabikp.bzscraper.accounts.application.port.in.AuditUseCase;
import sk.drabikp.bzscraper.accounts.application.port.in.BandsUseCase;
import sk.drabikp.bzscraper.accounts.application.port.in.MembersUseCase;
import sk.drabikp.bzscraper.accounts.application.port.in.UsersUseCase;
import sk.drabikp.bzscraper.gig.api.RefMapper;

import java.util.List;
import java.util.UUID;

/** Serves the user's bands (accounts.yaml); every call is the signed-in user's ({@link UsersUseCase}). */
@RestController
class BandsController implements BandsApi {

    private final UsersUseCase users;
    private final BandsUseCase bands;
    private final MembersUseCase members;
    private final AuditUseCase audit;
    private final AccountsMapper mapper;
    private final RefMapper refs;

    BandsController(UsersUseCase users, BandsUseCase bands, MembersUseCase members, AuditUseCase audit,
                    AccountsMapper mapper, RefMapper refs) {
        this.users = users;
        this.bands = bands;
        this.members = members;
        this.audit = audit;
        this.mapper = mapper;
        this.refs = refs;
    }

    @Override
    public ResponseEntity<List<MyBandResponse>> myBands() {
        return ResponseEntity.ok(mapper.toBandResponses(bands.myBands(users.current().orElseThrow().id())));
    }

    @Override
    public ResponseEntity<MyBandResponse> createBand(BandRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(
                bands.create(users.current().orElseThrow().id(), request.getName(), request.getSlug())));
    }

    @Override
    public ResponseEntity<MyBandResponse> band(UUID bandId) {
        return ResponseEntity.ok(mapper.toResponse(bands.band(users.current().orElseThrow().id(), refs.toBandId(bandId))));
    }

    @Override
    public ResponseEntity<MyBandResponse> updateBand(UUID bandId, BandRequest request) {
        return ResponseEntity.ok(mapper.toResponse(bands.update(users.current().orElseThrow().id(),
                refs.toBandId(bandId), request.getName(), request.getSlug(), request.getPublicList())));
    }

    @Override
    public ResponseEntity<Void> deleteBand(UUID bandId, String confirm) {
        bands.delete(users.current().orElseThrow().id(), refs.toBandId(bandId), confirm);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<AuditEntryResponse>> auditLog(UUID bandId, Integer limit) {
        return ResponseEntity.ok(mapper.toAuditResponses(audit.recent(users.current().orElseThrow().id(),
                refs.toBandId(bandId), limit == null ? 100 : limit)));
    }

    @Override
    public ResponseEntity<List<MemberResponse>> members(UUID bandId) {
        return ResponseEntity.ok(mapper.toMemberResponses(
                members.members(users.current().orElseThrow().id(), refs.toBandId(bandId))));
    }
}
