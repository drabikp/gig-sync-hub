package sk.drabikp.bzscraper.accounts.adapter.out.persistence;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import sk.drabikp.bzscraper.accounts.domain.Band;
import sk.drabikp.bzscraper.accounts.domain.Membership;
import sk.drabikp.bzscraper.accounts.domain.User;
import sk.drabikp.bzscraper.accounts.domain.UserId;
import sk.drabikp.bzscraper.gig.domain.BandId;

import java.util.UUID;

/** The accounts' rows as their domain (MapStruct); when a row was made is the store's. */
@Mapper
interface AccountsEntityMapper {

    @Mapping(target = "subject", source = "rauthySub")
    User toUser(AppUserEntity entity);

    @Mapping(target = "rauthySub", source = "subject")
    @Mapping(target = "createdAt", ignore = true)
    AppUserEntity toEntity(User user);

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
