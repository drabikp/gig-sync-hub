package sk.drabikp.bzscraper.sync.domain;

import org.jmolecules.ddd.annotation.ValueObject;
import sk.drabikp.bzscraper.gig.domain.Gig;

import java.util.Optional;

/**
 * Whether queued platform work is still needed, judged when it runs — tasks work from the
 * gig as it is then: a publish of a gig deleted, cancelled or already published meanwhile,
 * an update of a gig that is gone or not on the platform, … ends done with the reason.
 */
@ValueObject
public final class MootWork {

    private MootWork() {
    }

    /**
     * @param gig       the catalog's gig now, or null when it was deleted
     * @param published whether the gig is on the platform (a record of it is kept)
     */
    public static Optional<String> reason(SyncAction action, Gig gig, boolean published, String platformName) {
        return switch (action) {
            case PUBLISH -> gig == null ? Optional.of("the gig was deleted before it was published")
                    : gig.cancelled() ? Optional.of("the gig was cancelled before it was published")
                    : published ? Optional.of("already published") : Optional.empty();
            case UPDATE -> gig == null ? Optional.of("the gig is no longer in the catalog")
                    : !published ? Optional.of("not on " + platformName + " — nothing to do") : Optional.empty();
            case CANCEL, DELETE -> published ? Optional.empty() : Optional.of("not on the platform");
            case REACTIVATE -> gig == null ? Optional.of("the gig is no longer in the catalog")
                    : gig.cancelled() ? Optional.of("cancelled again before this ran — nothing to reactivate")
                    : Optional.empty();
        };
    }
}
