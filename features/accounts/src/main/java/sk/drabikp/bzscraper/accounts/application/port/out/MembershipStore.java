package sk.drabikp.bzscraper.accounts.application.port.out;

import sk.drabikp.bzscraper.accounts.domain.BandMembers;
import sk.drabikp.bzscraper.accounts.domain.Membership;
import sk.drabikp.bzscraper.accounts.domain.UserId;
import sk.drabikp.bzscraper.gig.domain.BandId;

import java.util.List;
import java.util.Optional;

public interface MembershipStore {

    Optional<Membership> of(UserId user, BandId band);

    /** Every band this user manages — their own memberships, not a band's roster. */
    List<Membership> ofUser(UserId user);

    /** The band's whole roster, which is what its "keeps an owner" rule is asked of. */
    BandMembers ofBand(BandId band);

    /**
     * Holds the band's roster for the rest of this transaction: changes to it queue up behind one
     * another instead of each deciding on a roster the other is already changing. Without it, two of
     * a band's owners leaving at the same moment each see the other one and the band is left with
     * none — which nothing on the pages can undo. Called BEFORE the roster is read, by every change
     * that could take an owner away (a role change, a removal, leaving, deleting an account).
     */
    void hold(BandId band);

    void save(Membership membership);

    void delete(UserId user, BandId band);
}
