package sk.drabikp.gigsynchub.check.domain;

import org.jmolecules.ddd.annotation.ValueObject;
import sk.drabikp.gigsynchub.gig.domain.GigId;
import sk.drabikp.gigsynchub.gig.domain.platform.Platform;

import java.util.List;

/**
 * A way a platform's copy of a published gig no longer matches the catalog — found by
 * reading the platform, not by anything the app did: a copy changed or deleted there by
 * hand, or placed in another town.
 *
 * @param differences what differs; empty for MISSING
 */
@ValueObject
public record Drift(Platform platform, GigId gigId, String gigLabel, String externalRef, Kind kind,
                    List<Difference> differences) {

    /**
     * One thing that differs: which {@link Field}, the catalog's value and the platform's (ISO
     * dates and times; {@code PLACE}: the town and how many km away the platform put it;
     * {@code CANCELLED}: "true"/"false"), and the English sentence ({@link #toString()}).
     */
    public record Difference(Field field, String catalog, String platform, String text) {

        @Override
        public String toString() {
            return text;
        }
    }

    public enum Field { DATE, TIME, NAME, VENUE, TOWN, COUNTRY, PLACE, CANCELLED }

    public enum Kind {
        /** The platform no longer lists the event the gig was published as. */
        MISSING,
        /** The platform's copy differs from the catalog. */
        DIFFERENT
    }

    public Drift {
        differences = List.copyOf(differences);
    }
}
