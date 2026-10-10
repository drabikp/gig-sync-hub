package sk.drabikp.bzscraper.accounts.adapter.in.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import sk.drabikp.bzscraper.accounts.adapter.in.rest.api.InvitationCreatedResponse;
import sk.drabikp.bzscraper.accounts.adapter.in.rest.api.InvitationOfferResponse;
import sk.drabikp.bzscraper.accounts.adapter.in.rest.api.InvitationRequest;
import sk.drabikp.bzscraper.accounts.adapter.in.rest.api.InvitationResponse;
import sk.drabikp.bzscraper.accounts.adapter.in.rest.api.InvitationsApi;
import sk.drabikp.bzscraper.accounts.adapter.in.rest.api.MyBandResponse;
import sk.drabikp.bzscraper.accounts.application.port.in.InvitationsUseCase;
import sk.drabikp.bzscraper.accounts.application.port.in.UsersUseCase;
import sk.drabikp.bzscraper.gig.domain.BandId;

import java.util.List;
import java.util.UUID;

/** Serves invitations into a band (accounts.yaml); every call is the signed-in user's. */
@RestController
class InvitationsController implements InvitationsApi {

    private final UsersUseCase users;
    private final InvitationsUseCase invitations;
    private final AccountsMapper mapper;

    InvitationsController(UsersUseCase users, InvitationsUseCase invitations, AccountsMapper mapper) {
        this.users = users;
        this.invitations = invitations;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<List<InvitationResponse>> openInvitations(UUID bandId) {
        return ResponseEntity.ok(mapper.toInvitationResponses(
                invitations.open(users.current().orElseThrow().id(), new BandId(bandId))));
    }

    @Override
    public ResponseEntity<InvitationCreatedResponse> invite(UUID bandId, InvitationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(invitations.invite(
                users.current().orElseThrow().id(), new BandId(bandId), request.getEmail(),
                mapper.toRole(request.getRole()))));
    }

    @Override
    public ResponseEntity<Void> revokeInvitation(UUID bandId, UUID invitationId) {
        invitations.revoke(users.current().orElseThrow().id(), new BandId(bandId), invitationId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<InvitationOfferResponse> invitationOffer(String token) {
        return ResponseEntity.ok(mapper.toResponse(invitations.offer(users.current().orElseThrow().id(), token)));
    }

    @Override
    public ResponseEntity<MyBandResponse> acceptInvitation(String token) {
        return ResponseEntity.ok(mapper.toResponse(invitations.accept(users.current().orElseThrow().id(), token)));
    }
}
