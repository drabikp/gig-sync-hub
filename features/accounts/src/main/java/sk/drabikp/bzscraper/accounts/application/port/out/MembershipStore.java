package sk.drabikp.bzscraper.accounts.application.port.out;

import sk.drabikp.bzscraper.accounts.domain.Membership;
import sk.drabikp.bzscraper.accounts.domain.UserId;
import sk.drabikp.bzscraper.gig.domain.BandId;

import java.util.List;
import java.util.Optional;

public interface MembershipStore {

    Optional<Membership> of(UserId user, BandId band);

    List<Membership> ofUser(UserId user);

    List<Membership> ofBand(BandId band);

    void save(Membership membership);
}
