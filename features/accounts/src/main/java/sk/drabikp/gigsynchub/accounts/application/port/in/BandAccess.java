package sk.drabikp.gigsynchub.accounts.application.port.in;

import sk.drabikp.gigsynchub.accounts.domain.Role;
import sk.drabikp.gigsynchub.accounts.domain.UserId;
import sk.drabikp.gigsynchub.gig.domain.BandId;

import java.util.Optional;

/** Whether, and as what, a user may use a band — every band's address is checked with it. */
public interface BandAccess {

    /** The user's role in the band; empty when they aren't its member. */
    Optional<Role> role(UserId user, BandId band);
}
