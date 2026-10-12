package sk.drabikp.gigsynchub.accounts.domain;

import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;

import java.util.Objects;

/**
 * Someone who signed in: who Rauthy says they are ({@code subject}, Rauthy's id for the login), with
 * the e-mail and name it gave. Made at the first sign-in.
 */
@AggregateRoot
public record User(@Identity UserId id, String subject, String email, String displayName) {

    public User {
        Objects.requireNonNull(id, "a user's id");
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("a user's login (subject) is required");
        }
    }

    /** How the user appears: the name, else the e-mail. */
    public String shownAs() {
        return displayName != null && !displayName.isBlank() ? displayName : email;
    }
}
