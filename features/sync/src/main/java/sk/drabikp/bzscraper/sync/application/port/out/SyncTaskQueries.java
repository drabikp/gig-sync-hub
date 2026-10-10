package sk.drabikp.bzscraper.sync.application.port.out;

import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.sync.domain.SyncAction;
import sk.drabikp.bzscraper.sync.domain.SyncLogEntry;
import sk.drabikp.bzscraper.sync.domain.SyncTask;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reading the sync outbox ({@link SyncOutbox}): its tasks and their history, without changing them — a
 * band's, but for the worker's questions across bands (what is due, what was running).
 */
public interface SyncTaskQueries {

    Optional<SyncTask> find(long id);

    /** PENDING and RUNNING tasks of the band's gig. */
    List<SyncTask> openFor(BandId band, GigId gigId);

    /** The band's tasks not settled for good: PENDING, RUNNING and FAILED. */
    List<SyncTask> unfinished(BandId band);

    /** The band's newest tasks first. */
    List<SyncTask> recent(BandId band, int limit);

    /** The gig's newest tasks first (whatever else was done meanwhile). */
    List<SyncTask> history(BandId band, GigId gigId, int limit);

    List<SyncLogEntry> log(long taskId);

    /** The oldest PENDING task that is due, of any band. */
    Optional<SyncTask> nextDue(Instant now);

    /**
     * The next due task of any band but those {@code paused}, on a platform other than those held back by
     * their breaker ({@code skipping}).
     */
    default Optional<SyncTask> nextDue(Instant now, Set<Platform> skipping, Set<BandId> paused) {
        return skipping.isEmpty() && paused.isEmpty() ? nextDue(now) : due(now).stream()
                .filter(t -> !skipping.contains(t.platform()) && !paused.contains(t.band())).findFirst();
    }

    /** Every due task of any band, oldest first. */
    List<SyncTask> due(Instant now);

    /** Every due PENDING task of the band of this action on this platform, oldest first (to run as one batch). */
    List<SyncTask> due(BandId band, Platform platform, SyncAction action, Instant now);

    /** The RUNNING tasks of any band (after a restart, to settle them). */
    List<SyncTask> running();
}
