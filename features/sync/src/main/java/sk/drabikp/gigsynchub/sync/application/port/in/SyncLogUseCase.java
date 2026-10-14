package sk.drabikp.gigsynchub.sync.application.port.in;

import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.gig.domain.GigId;
import sk.drabikp.gigsynchub.sync.domain.SyncCounts;
import sk.drabikp.gigsynchub.sync.domain.SyncLogEntry;
import sk.drabikp.gigsynchub.sync.domain.SyncTask;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** The sync log: what was, is and will be done on the platforms, and the user's say on failures. */
public interface SyncLogUseCase {

    /** The band's tasks not settled for good (pending, running, failed). */
    List<SyncTask> unfinished(BandId band);

    /** The gig's newest tasks first. */
    List<SyncTask> history(BandId band, GigId gigId, int limit);

    /** Which tasks a list shows: the latest ones, those not finished (failed included), or those waiting for the user. */
    enum Show { ALL, OPEN, FAILED }

    /** The tasks to list, newest first. */
    List<SyncTask> tasks(BandId band, Show show);

    /** The unfinished work (failed included) by what it is doing now. */
    SyncCounts counts(BandId band);

    /**
     * The same four numbers for several bands in one read — the operator's gauges, which ask about
     * every band there is and asked once per band until October 2026.
     */
    Map<BandId, SyncCounts> counts(Collection<BandId> bands);

    /** A task's history; NotFoundException when it isn't the band's. */
    List<SyncLogEntry> log(BandId band, long taskId);

    /** Runs a failed (or discarded) task again. */
    void retry(BandId band, long taskId);

    /** Gives up on a pending or failed task — the platform is then left as it is. */
    void discard(BandId band, long taskId);
}
