package sk.drabikp.gigsynchub.accounts.domain;

import org.jmolecules.ddd.annotation.ValueObject;

import java.time.Instant;
import java.util.Objects;

/**
 * What the last test of a band's platform account said: it signed in ({@code ok}) or why not, and when.
 */
@ValueObject
public record AccountCheck(boolean ok, String message, Instant at) {

    public AccountCheck {
        Objects.requireNonNull(at, "when the account was checked");
        message = message == null || message.isBlank() ? null : message.strip();
    }

    /** A check's outcome before it is dated. */
    public record Outcome(boolean ok, String message) {

        public static Outcome signedIn(String message) {
            return new Outcome(true, message);
        }

        public static Outcome failed(String message) {
            return new Outcome(false, message);
        }

        public AccountCheck at(Instant at) {
            return new AccountCheck(ok, message, at);
        }
    }
}
