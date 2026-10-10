package sk.drabikp.bzscraper.accounts.application.port.in;

import sk.drabikp.bzscraper.accounts.domain.User;

import java.util.Optional;

/** The user signed in on this request — made at the first sign-in (from what Rauthy says). */
public interface UsersUseCase {

    /** The signed-in user; empty when nobody is signed in. */
    Optional<User> current();
}
