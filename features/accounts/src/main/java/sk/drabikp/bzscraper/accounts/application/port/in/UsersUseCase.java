package sk.drabikp.bzscraper.accounts.application.port.in;

import sk.drabikp.bzscraper.accounts.domain.User;
import sk.drabikp.bzscraper.accounts.domain.UserId;

import java.util.Optional;

/** The user signed in on this request — made at the first sign-in (from what Rauthy says). */
public interface UsersUseCase {

    /** The signed-in user; empty when nobody is signed in. */
    Optional<User> current();

    /**
     * Deletes the user and their memberships; a band of which they are the only member goes with them.
     * Refused ({@link AccountsErrorCode#LAST_OWNER}, {@code band}) while they are the only owner of a band
     * that has other members — they hand it over first. Their Rauthy login stays (Rauthy's to delete).
     */
    void deleteMe(UserId user);
}
