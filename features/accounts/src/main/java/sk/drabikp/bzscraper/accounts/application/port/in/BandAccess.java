package sk.drabikp.bzscraper.accounts.application.port.in;

import sk.drabikp.bzscraper.accounts.domain.Role;
import sk.drabikp.bzscraper.accounts.domain.UserId;
import sk.drabikp.bzscraper.gig.domain.BandId;

import java.util.Optional;

/** Whether, and as what, a user may use a band — every band's address is checked with it. */
public interface BandAccess {

    /** The user's role in the band; empty when they aren't its member. */
    Optional<Role> role(UserId user, BandId band);
}
