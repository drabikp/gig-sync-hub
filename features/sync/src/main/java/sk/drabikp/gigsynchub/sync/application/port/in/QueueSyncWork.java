package sk.drabikp.gigsynchub.sync.application.port.in;

import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.gig.domain.Gig;
import sk.drabikp.gigsynchub.gig.domain.GigId;
import sk.drabikp.gigsynchub.sync.domain.QueueResult;

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

    /**
     * The gig reads differently now (an edit moved or renamed it): its waiting work says so too.
     * Nothing follows the gig — its identity did not change — only how it is named in the log.
     */
    void relabel(BandId band, Gig gig);

    /** Tells the worker and the pages; after the transaction committed. */
    void signal(BandId band, QueueResult result);
}
