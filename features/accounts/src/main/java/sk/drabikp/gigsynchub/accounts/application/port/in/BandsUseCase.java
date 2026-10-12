package sk.drabikp.gigsynchub.accounts.application.port.in;

import sk.drabikp.gigsynchub.accounts.domain.Band;
import sk.drabikp.gigsynchub.accounts.domain.Role;
import sk.drabikp.gigsynchub.accounts.domain.UserId;
import sk.drabikp.gigsynchub.gig.domain.BandId;

import java.util.List;

/** The bands a user manages: listing them, making a new one (its owner), renaming one. */
public interface BandsUseCase {

    /** A band as one of its members sees it. */
    record MyBand(Band band, Role role, String displayName) {
    }

    /** The user's bands, by name. */
    List<MyBand> myBands(UserId user);

    /** The user's band; NotFoundException when they aren't its member (or it isn't there). */
    MyBand band(UserId user, BandId band);

    /**
     * A new band, the user its owner; refused when the address is taken, the name or address isn't one, or the
     * user owns as many bands as one may already.
     */
    MyBand create(UserId user, String name, String slug);

    /** A new name and address, and whether its gigs are listed publicly (null: as it was) — owners only. */
    MyBand update(UserId user, BandId band, String name, String slug, Boolean publicList);

    /**
     * Deletes the band and everything it has — gigs, platform work, calendar, accounts, members (owners only;
     * {@code confirmSlug} must be its address). Nothing changes on the platforms.
     */
    void delete(UserId owner, BandId band, String confirmSlug);
}
