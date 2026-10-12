package sk.drabikp.gigsynchub.sync.application.port.out;

import org.jmolecules.ddd.annotation.Repository;
import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.gig.domain.GigId;
import sk.drabikp.gigsynchub.gig.domain.platform.Platform;
import sk.drabikp.gigsynchub.sync.domain.SyncAction;
import sk.drabikp.gigsynchub.sync.domain.SyncLogEntry;
import sk.drabikp.gigsynchub.sync.domain.SyncTask;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Reading the sync outbox ({@link SyncOutbox}): its tasks and their history, without changing them — a
 * band's, but for the worker's questions across bands (what is due, what was running).
 */
@Repository
public interface SyncTaskQueries {

    Optional<SyncTask> find(long id);

    /** PENDING and RUNNING tasks of the band's gig. */
    List<SyncTask> openFor(BandId band, GigId gigId);

    /** The band's tasks not settled for good: PENDING, RUNNING and FAILED. */
    List<SyncTask> unfinished(BandId band);

    /** The band's latest activity first ({@link SyncTask#LATEST_FIRST}), and the latest {@code limit} of it. */
    List<SyncTask> recent(BandId band, int limit);

    /** The gig's latest activity first ({@link SyncTask#LATEST_FIRST}), whatever else was done meanwhile. */
    List<SyncTask> history(BandId band, GigId gigId, int limit);

    List<SyncLogEntry> log(long taskId);

    /** The oldest PENDING task that is due, of any band. */
    Optional<SyncTask> nextDue(Instant now);

    /** Every due task of any band, oldest first. */
    List<SyncTask> due(Instant now);

    /** Every due PENDING task of the band of this action on this platform, oldest first (to run as one batch). */
    List<SyncTask> due(BandId band, Platform platform, SyncAction action, Instant now);

    /** The RUNNING tasks of any band (after a restart, to settle them). */
    List<SyncTask> running();
}
