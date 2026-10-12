package sk.drabikp.gigsynchub.accounts.domain;

import org.jmolecules.ddd.annotation.ValueObject;
import sk.drabikp.gigsynchub.gig.domain.BandId;

import java.time.Instant;
import java.util.Objects;

/**
 * A change made in a band through the API: by whom (null: the user is gone), which operation
 * ({@code action}, the API's operation id, e.g. {@code updateGig}), what it named ({@code target}: the
 * address's own part, e.g. a gig's token), how it ended (the HTTP status), when.
 */
@ValueObject
public record AuditEvent(BandId band, UserId user, String action, String target, int status, Instant at) {

    public AuditEvent {
        Objects.requireNonNull(band, "the band");
        Objects.requireNonNull(action, "the action");
        Objects.requireNonNull(at, "when");
        target = target == null || target.isBlank() ? null : target.length() > 300 ? target.substring(0, 300) : target;
    }
}
