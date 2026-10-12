package sk.drabikp.gigsynchub.accounts.application.port.out;

import java.util.Optional;

/**
 * Who is signed in on the request being served — as the sign-in (Rauthy) says: its id for the login
 * ({@code subject}), the e-mail and name. Implemented where the sign-in is known (the app's web setup).
 */
public interface CurrentLogin {

    /** The signed-in login; empty when nobody is signed in. */
    Optional<Login> current();

    record Login(String subject, String email, String name) {
    }
}
