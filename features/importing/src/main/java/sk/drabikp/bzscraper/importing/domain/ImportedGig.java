package sk.drabikp.bzscraper.importing.domain;

import sk.drabikp.bzscraper.gig.domain.Coordinates;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;

import java.util.Optional;

/**
 * A gig as it exists on a platform, read for import: the platform's version of the gig
 * and the platform's id for it ({@code externalRef}), so importing can link the catalog
 * gig to that existing event instead of publishing a duplicate.
 *
 * <p>No jMolecules stereotype on purpose: A gig as a platform hands it over — it HOLDS an aggregate rather than being a value
 * of one, which is what a read model does and what {@code valueObjectsMustNotReferToIdentifiables}
 * rightly refuses to call a value object.
 */
public record ImportedGig(Platform platform, Gig gig, String externalRef, Double latitude, Double longitude) {

    public ImportedGig {
        if (platform == null || gig == null || externalRef == null || externalRef.isBlank()) {
            throw new IllegalArgumentException("an imported gig needs its platform, gig and platform id");
        }
        if ((latitude == null) != (longitude == null)) {
            throw new IllegalArgumentException("coordinates need both latitude and longitude");
        }
    }

    /** A platform copy without coordinates (the platform shows none). */
    public ImportedGig(Platform platform, Gig gig, String externalRef) {
        this(platform, gig, externalRef, null, null);
    }

    /** Where the platform put the event, when it shows that. */
    public Optional<Coordinates> coordinates() {
        return latitude == null ? Optional.empty() : Optional.of(new Coordinates(latitude, longitude));
    }
}
