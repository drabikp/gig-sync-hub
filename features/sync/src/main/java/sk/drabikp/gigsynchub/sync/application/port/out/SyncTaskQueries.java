package sk.drabikp.gigsynchub.sync.application.port.out;

import org.jmolecules.ddd.annotation.Repository;
import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.gig.domain.GigId;
import sk.drabikp.gigsynchub.gig.domain.platform.Platform;
import sk.drabikp.gigsynchub.sync.domain.SyncAction;
import sk.drabikp.gigsynchub.sync.domain.SyncCounts;
import sk.drabikp.gigsynchub.sync.domain.SyncLogEntry;
import sk.drabikp.gigsynchub.sync.domain.SyncTask;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reading the sync outbox ({@link SyncOutbox}): its tasks and their history, without changing them — a
 * band's, but for the worker's questions across bands (what is due, what was running).
 */
@Repository
public interface SyncTaskQueries {

    Optional<SyncTask> find(BandId band, long id);

    /** PENDING and RUNNING tasks of the band's gig. */
    List<SyncTask> openFor(BandId band, GigId gigId);

    /** The band's tasks not settled for good: PENDING, RUNNING and FAILED. */
    List<SyncTask> unfinished(BandId band);

    /**
     * The same open work COUNTED, for the bands asked about at once — the four numbers the Activity
     * page's heading and the operator's gauges show.
     *
     * <p>Counted by the store, deliberately. Both callers used to ask {@link #unfinished(BandId)}
     * and count in Java, which reads every open row to answer with four numbers: a band with a
     * thousand queued gigs paid a thousand rows for its heading, and the operator's gauges paid
     * every band's, every minute. A band with nothing open is simply missing from the answer.
     */
    Map<BandId, SyncCounts> openWork(Collection<BandId> bands);

    /** The band's latest activity first ({@link SyncTask#LATEST_FIRST}), and the latest {@code limit} of it. */
    List<SyncTask> recent(BandId band, int limit);

    /** The gig's latest activity first ({@link SyncTask#LATEST_FIRST}), whatever else was done meanwhile. */
    List<SyncTask> history(BandId band, GigId gigId, int limit);

    List<SyncLogEntry> log(BandId band, long taskId);

    /** Every due task of any band, oldest first. */
    List<SyncTask> due(Instant now);

    /**
     * The bands that have work due at all — one entry per band, whatever each of them is waiting to
     * do. Whose turn it is can then be decided without reading anybody's work: see {@code FairTurns}.
     */
    List<BandId> bandsWithWorkDue(Instant now);

    /** One band's due work, oldest first, at most {@code limit} of it. */
    List<SyncTask> due(BandId band, Instant now, int limit);

    /** Every due PENDING task of the band of this action on this platform, oldest first (to run as one batch). */
    List<SyncTask> due(BandId band, Platform platform, SyncAction action, Instant now);

    /** The RUNNING tasks of any band (after a restart, to settle them). */
    List<SyncTask> running();
}
