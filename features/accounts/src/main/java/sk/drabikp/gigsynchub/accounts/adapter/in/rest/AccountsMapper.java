package sk.drabikp.gigsynchub.accounts.adapter.in.rest;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import sk.drabikp.gigsynchub.accounts.adapter.in.rest.api.AccountCheckResponse;
import sk.drabikp.gigsynchub.accounts.adapter.in.rest.api.AuditEntryResponse;
import sk.drabikp.gigsynchub.accounts.adapter.in.rest.api.InvitationCreatedResponse;
import sk.drabikp.gigsynchub.accounts.adapter.in.rest.api.InvitationOfferResponse;
import sk.drabikp.gigsynchub.accounts.adapter.in.rest.api.InvitationResponse;
import sk.drabikp.gigsynchub.accounts.adapter.in.rest.api.MemberResponse;
import sk.drabikp.gigsynchub.accounts.adapter.in.rest.api.MyBandResponse;
import sk.drabikp.gigsynchub.accounts.adapter.in.rest.api.PlatformAccountResponse;
import sk.drabikp.gigsynchub.accounts.adapter.in.rest.api.RoleValue;
import sk.drabikp.gigsynchub.accounts.application.port.in.AuditUseCase.Entry;
import sk.drabikp.gigsynchub.accounts.application.port.in.BandsUseCase.MyBand;
import sk.drabikp.gigsynchub.accounts.application.port.in.InvitationsUseCase.Created;
import sk.drabikp.gigsynchub.accounts.application.port.in.InvitationsUseCase.Offer;
import sk.drabikp.gigsynchub.accounts.application.port.in.MembersUseCase.Member;
import sk.drabikp.gigsynchub.accounts.application.port.in.PlatformAccountsUseCase.AccountView;
import sk.drabikp.gigsynchub.accounts.domain.AccountCheck;
import sk.drabikp.gigsynchub.accounts.domain.Invitation;
import sk.drabikp.gigsynchub.accounts.domain.Role;
import sk.drabikp.gigsynchub.accounts.domain.UserId;
import sk.drabikp.gigsynchub.gig.api.RefMapper;

import java.util.List;
import java.util.UUID;

/** Bands, members and platform accounts as the API sends them (accounts.yaml). */
@Mapper(uses = RefMapper.class)
interface AccountsMapper {

    @Mapping(target = "id", source = "band.id")
    @Mapping(target = "slug", source = "band.slug")
    @Mapping(target = "name", source = "band.name")
    @Mapping(target = "publicList", source = "band.publicList")
    MyBandResponse toResponse(MyBand band);

    List<MyBandResponse> toBandResponses(List<MyBand> bands);

    @Mapping(target = "id", source = "user.id")
    @Mapping(target = "name", expression = "java(member.user().shownAs())")
    @Mapping(target = "email", source = "user.email")
    MemberResponse toResponse(Member member);

    List<MemberResponse> toMemberResponses(List<Member> members);

    PlatformAccountResponse toResponse(AccountView account);

    List<PlatformAccountResponse> toAccountResponses(List<AccountView> accounts);

    AccountCheckResponse toResponse(AccountCheck check);

    InvitationResponse toResponse(Invitation invitation);

    List<InvitationResponse> toInvitationResponses(List<Invitation> invitations);

    InvitationCreatedResponse toResponse(Created created);

    InvitationOfferResponse toResponse(Offer offer);

    @Mapping(target = "action", source = "event.action")
    @Mapping(target = "target", source = "event.target")
    @Mapping(target = "status", source = "event.status")
    @Mapping(target = "at", source = "event.at")
    AuditEntryResponse toResponse(Entry entry);

    List<AuditEntryResponse> toAuditResponses(List<Entry> entries);

    Role toRole(RoleValue role);

    default UUID toUuid(UserId id) {
        return id == null ? null : id.value();
    }
}
