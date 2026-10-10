package sk.drabikp.bzscraper.accounts.application.port.in;

import sk.drabikp.bzscraper.accounts.domain.Role;
import sk.drabikp.bzscraper.accounts.domain.User;
import sk.drabikp.bzscraper.accounts.domain.UserId;
import sk.drabikp.bzscraper.gig.domain.BandId;

import java.util.List;

/** Who manages a band. */
public interface MembersUseCase {

    record Member(User user, Role role, String displayName) {
    }

    /** The band's members, owners first (asked by one of them). */
    List<Member> members(UserId asker, BandId band);
}
