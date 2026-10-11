package sk.drabikp.bzscraper.gig.domain;

import org.jmolecules.ddd.annotation.ValueObject;

import java.time.ZonedDateTime;

/**
 * Value object: when the band itself plays within a longer event (a festival running for
 * days, a club night with several bands). {@code end} is optional and, when present, not
 * before {@code start}.
 */
@ValueObject
public record Slot(ZonedDateTime start, ZonedDateTime end) {

    public Slot {
        if (start == null) {
            throw new IllegalArgumentException("slot start is required");
        }
        if (end != null && end.isBefore(start)) {
            throw new IllegalArgumentException("slot end must not be before its start");
        }
    }
}
