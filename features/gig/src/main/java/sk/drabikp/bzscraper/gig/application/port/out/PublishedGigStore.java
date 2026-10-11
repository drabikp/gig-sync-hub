package sk.drabikp.bzscraper.gig.application.port.out;

import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.gig.domain.platform.Publication;

import java.util.List;
import java.util.Set;
import java.util.Optional;

/**
 * Local record of what each band has published to each platform. Keeps idempotency (a gig already
 * published is not re-published) and, when the platform gives one back, the platform's external id
 * ({@code externalRef}) so the gig can later be updated, cancelled or deleted there. Keyed by the
 * band, the {@link Platform} and the {@link GigId}.
 */
public interface PublishedGigStore {

    boolean isPublished(BandId band, Platform platform, GigId gigId);

    /** The platform's id for a published gig, if the platform returned one. */
    Optional<String> externalRef(BandId band, Platform platform, GigId gigId);

    /** Records a successful publish; {@code externalRef} may be null if unknown. */
    void record(BandId band, Platform platform, GigId gigId, String externalRef);

    /** Forgets a gig on a platform (after it has been removed there). */
    void remove(BandId band, Platform platform, GigId gigId);

    /**
     * Re-keys every platform's record of a gig whose identity changed (an edit moved its date or
     * venue), keeping the external ids. No-op if nothing was recorded under {@code from}.
     */
    void move(BandId band, GigId from, GigId to);

    /** Every publication of the band. Only for work that is about all of them — a page asks {@link #of}. */
    List<Publication> all(BandId band);

    /**
     * The publications of just these gigs — what a PAGE of the list needs, so its cost follows the
     * page rather than the band's whole catalog. An empty set reads nothing.
     */
    List<Publication> of(BandId band, Set<GigId> gigIds);
}
