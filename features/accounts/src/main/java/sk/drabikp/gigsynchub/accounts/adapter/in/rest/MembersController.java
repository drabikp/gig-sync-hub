package sk.drabikp.gigsynchub.accounts.adapter.in.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import sk.drabikp.gigsynchub.accounts.adapter.in.rest.api.MemberResponse;
import sk.drabikp.gigsynchub.accounts.adapter.in.rest.api.MemberRoleRequest;
import sk.drabikp.gigsynchub.accounts.adapter.in.rest.api.MembersApi;
import sk.drabikp.gigsynchub.accounts.application.port.in.MembersUseCase;
import sk.drabikp.gigsynchub.accounts.application.port.in.UsersUseCase;
import sk.drabikp.gigsynchub.accounts.domain.UserId;
import sk.drabikp.gigsynchub.gig.domain.BandId;

import java.util.UUID;

/** Serves a band's member management (accounts.yaml); every call is the signed-in user's. */
@RestController
class MembersController implements MembersApi {

    private final UsersUseCase users;
    private final MembersUseCase members;
    private final AccountsMapper mapper;

    MembersController(UsersUseCase users, MembersUseCase members, AccountsMapper mapper) {
        this.users = users;
        this.members = members;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<MemberResponse> changeMemberRole(UUID bandId, UUID userId, MemberRoleRequest request) {
        return ResponseEntity.ok(mapper.toResponse(members.changeRole(users.current().orElseThrow().id(),
                new BandId(bandId), new UserId(userId), mapper.toRole(request.getRole()))));
    }

    @Override
    public ResponseEntity<Void> removeMember(UUID bandId, UUID userId) {
        members.remove(users.current().orElseThrow().id(), new BandId(bandId), new UserId(userId));
        return ResponseEntity.noContent().build();
    }
}
