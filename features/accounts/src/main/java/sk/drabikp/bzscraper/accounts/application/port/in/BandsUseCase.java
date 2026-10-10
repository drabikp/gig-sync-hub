package sk.drabikp.bzscraper.accounts.application.port.in;

import sk.drabikp.bzscraper.accounts.domain.Band;
import sk.drabikp.bzscraper.accounts.domain.Role;
import sk.drabikp.bzscraper.accounts.domain.UserId;
import sk.drabikp.bzscraper.gig.domain.BandId;

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

    /** A new band, the user its owner; refused when the address is taken or the name or address isn't one. */
    MyBand create(UserId user, String name, String slug);

    /** A new name and address (owners only). */
    MyBand update(UserId user, BandId band, String name, String slug);
}
