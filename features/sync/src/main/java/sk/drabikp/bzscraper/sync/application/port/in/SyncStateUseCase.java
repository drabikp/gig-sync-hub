package sk.drabikp.bzscraper.sync.application.port.in;

import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;

import java.util.Set;

/** Where the sync is, for whoever must not get in its way (read-only). */
public interface SyncStateUseCase {

    /** Whether the gig's platform work is running right now (then the gig must not change). */
    boolean busy(BandId band, GigId gigId);

    /** The band's gigs with platform work queued, running or waiting for the user. */
    Set<GigId> gigsWithOpenWork(BandId band);

    /** Whether the platform's work is held back by its circuit breaker right now. */
    boolean heldBack(Platform platform);
}
