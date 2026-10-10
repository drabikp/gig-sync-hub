package sk.drabikp.bzscraper.accounts.domain;

import java.util.Objects;
import java.util.UUID;

/** A user of the app (one Rauthy login). */
public record UserId(UUID value) {

    public UserId {
        Objects.requireNonNull(value, "a user's id");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
