package sk.drabikp.bzscraper.sync.application.port.in;

import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.sync.domain.SyncCounts;
import sk.drabikp.bzscraper.sync.domain.SyncLogEntry;
import sk.drabikp.bzscraper.sync.domain.SyncTask;

import java.util.List;

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

    /** A task's history; NotFoundException when it isn't the band's. */
    List<SyncLogEntry> log(BandId band, long taskId);

    /** Runs a failed (or discarded) task again. */
    void retry(BandId band, long taskId);

    /** Gives up on a pending or failed task — the platform is then left as it is. */
    void discard(BandId band, long taskId);
}
