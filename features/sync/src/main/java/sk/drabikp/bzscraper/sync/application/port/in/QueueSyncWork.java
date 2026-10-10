package sk.drabikp.bzscraper.sync.application.port.in;

import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.sync.domain.QueueResult;

/**
 * What a change to a band's catalog puts in the sync outbox — the platforms follow the catalog
 * through it. Call INSIDE the transaction of the change (outbox pattern: the change and its platform
 * work commit together), then {@link #signal} once it committed.
 */
public interface QueueSyncWork {

    /** The gig's details changed: an update where it is published (or being published). */
    QueueResult update(BandId band, Gig gig);

    QueueResult cancel(BandId band, Gig gig);

    QueueResult reactivate(BandId band, Gig gig);

    /** {@code gig} is the deleted catalog gig (null if it wasn't there). */
    QueueResult delete(BandId band, GigId id, Gig gig);

    /** The gig's identity moved: its queued work goes along. */
    void move(BandId band, GigId from, Gig to);

    /** Tells the worker and the pages; after the transaction committed. */
    void signal(BandId band, QueueResult result);
}
