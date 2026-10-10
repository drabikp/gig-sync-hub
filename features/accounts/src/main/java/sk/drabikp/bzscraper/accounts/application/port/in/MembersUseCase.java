package sk.drabikp.bzscraper.accounts.application.port.in;

import sk.drabikp.bzscraper.accounts.domain.Role;
import sk.drabikp.bzscraper.accounts.domain.User;
import sk.drabikp.bzscraper.accounts.domain.UserId;
import sk.drabikp.bzscraper.gig.domain.BandId;

import java.util.List;

/**
 * Who manages a band: listing them (members), changing a member's role and removing a member (owners),
 * leaving (anyone — removing oneself). A band keeps at least one owner ({@link AccountsErrorCode#LAST_OWNER}):
 * an owner hands it over (makes another member an owner) before stepping down.
 */
public interface MembersUseCase {

    record Member(User user, Role role, String displayName) {
    }

    /** The band's members, owners first (asked by one of them). */
    List<Member> members(UserId asker, BandId band);

    /** Makes the member an owner or a manager (owners only). */
    Member changeRole(UserId owner, BandId band, UserId member, Role role);

    /** Removes the member — an owner anyone, anyone themselves (leaving). */
    void remove(UserId asker, BandId band, UserId member);
}
