package sk.drabikp.gigsynchub.accounts.adapter.out.persistence;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import sk.drabikp.gigsynchub.accounts.domain.AuditEvent;
import sk.drabikp.gigsynchub.accounts.domain.Band;
import sk.drabikp.gigsynchub.accounts.domain.Invitation;
import sk.drabikp.gigsynchub.accounts.domain.Membership;
import sk.drabikp.gigsynchub.accounts.domain.User;
import sk.drabikp.gigsynchub.accounts.domain.UserId;
import sk.drabikp.gigsynchub.gig.domain.BandId;

import java.util.UUID;

/** The accounts' rows as their domain (MapStruct); when a row was made is the store's, an invitation's token hash too. */
@Mapper
interface AccountsEntityMapper {

    @Mapping(target = "subject", source = "rauthySub")
    User toUser(AppUserEntity entity);

    @Mapping(target = "rauthySub", source = "subject")
    @Mapping(target = "createdAt", ignore = true)
    AppUserEntity toEntity(User user);

    @Mapping(target = "listedPublicly", ignore = true)       // Band.listedPublicly(…) looks like a fluent setter
    Band toBand(BandEntity entity);

    @Mapping(target = "createdAt", ignore = true)
    BandEntity toEntity(Band band);

    @Mapping(target = "user", source = "userId")
    @Mapping(target = "band", source = "bandId")
    Membership toMembership(MembershipEntity entity);

    @Mapping(target = "userId", source = "user")
    @Mapping(target = "bandId", source = "band")
    @Mapping(target = "createdAt", ignore = true)
    MembershipEntity toEntity(Membership membership);

    @Mapping(target = "band", source = "bandId")
    @Mapping(target = "accepted", ignore = true)            // Invitation.accepted(at) is no property
    Invitation toInvitation(InvitationEntity entity);

    @Mapping(target = "bandId", source = "band")
    @Mapping(target = "tokenHash", ignore = true)
    InvitationEntity toEntity(Invitation invitation);

    @Mapping(target = "band", source = "bandId")
    @Mapping(target = "user", source = "userId")
    AuditEvent toEvent(AuditEventEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "bandId", source = "band")
    @Mapping(target = "userId", source = "user")
    AuditEventEntity toEntity(AuditEvent event);

    default UserId toUserId(UUID id) {
        return id == null ? null : new UserId(id);
    }

    default UUID toUuid(UserId id) {
        return id == null ? null : id.value();
    }

    default BandId toBandId(UUID id) {
        return id == null ? null : new BandId(id);
    }

    default UUID toUuid(BandId id) {
        return id == null ? null : id.value();
    }
}
