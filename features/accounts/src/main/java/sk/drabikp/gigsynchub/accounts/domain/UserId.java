package sk.drabikp.gigsynchub.accounts.domain;

import org.jmolecules.ddd.annotation.ValueObject;

import java.util.Objects;
import java.util.UUID;

/** A user of the app (one Rauthy login). */
@ValueObject
public record UserId(UUID value) {

    public UserId {
        Objects.requireNonNull(value, "a user's id");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
