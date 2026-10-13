package sk.drabikp.gigsynchub.sync.application.port.out;

import org.jmolecules.ddd.annotation.Repository;
import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.gig.domain.GigId;
import sk.drabikp.gigsynchub.gig.domain.platform.Platform;
import sk.drabikp.gigsynchub.sync.domain.StepType;
import sk.drabikp.gigsynchub.sync.domain.SyncAction;
import sk.drabikp.gigsynchub.sync.domain.SyncTask;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * The sync outbox: platform work waiting to be done, being done and done, with each
 * task's history. Writes join the caller's transaction, so a catalog change and the tasks
 * it needs commit (or roll back) together. Every state change is logged. Who only reads it
 * uses {@link SyncTaskQueries}.
 *
 * <p><b>Every one of these names the band, the id alone never being enough.</b> A task's id comes
 * from a sequence every band draws on, so it is guessable, and until October 2026 the whole of this
 * write path took the id by itself — a crafted number reached another band's work, and the only
 * thing in the way was whichever caller remembered to check. The band cannot be left out now, and
 * {@code BandScopedQueriesTest} is what keeps a new method from leaving it out again.
 */
@Repository
public interface SyncOutbox extends SyncTaskQueries {

    /** Adds a PENDING task, due now. */
    SyncTask enqueue(BandId band, GigId gigId, String gigLabel, Platform platform, SyncAction action, Instant now);

    /** Discards the gig's PENDING tasks on the platform, logging {@code why}; returns them. */
    List<SyncTask> supersede(BandId band, GigId gigId, Platform platform, String why, Instant now);

    /**
     * Discards the gig's FAILED tasks of these actions on the platform — a newer task does
     * their work now — logging {@code why}; returns them.
     */
    List<SyncTask> replaceFailed(BandId band, GigId gigId, Platform platform, Set<SyncAction> actions, String why,
                                 Instant now);

    /**
     * Says how the gig reads NOW on the work that has not happened yet (PENDING, RUNNING) — an edit
     * may have moved its day or renamed it. What is settled keeps the label it was done with: the
     * log says what the work was about when it ran, not what the gig became afterwards.
     */
    void relabel(BandId band, GigId gigId, String gigLabel);

    /** RUNNING, one more attempt — only if still PENDING (the user may have discarded it); false otherwise. */
    boolean markRunning(BandId band, long id, Instant now);

    void markDone(BandId band, long id, String message, Instant now);

    /** Back to PENDING until {@code nextAttempt}. */
    void markRetry(BandId band, long id, String error, Instant nextAttempt, Instant now);

    void markFailed(BandId band, long id, String error, Instant now);

    /** A RUNNING task that couldn't start after all: PENDING until {@code nextAttempt}, the attempt not counted. */
    void postpone(BandId band, long id, String why, Instant nextAttempt, Instant now);

    /** A workflow run moves on to {@code next}: PENDING, due now, logging {@code why}. */
    void advance(BandId band, long id, StepType next, String why, Instant now);

    /** The user's retry of a FAILED or DISCARDED task: PENDING now, attempts counted afresh. */
    void requeue(BandId band, long id, Instant now);

    /** The user's discard of a PENDING or FAILED task. */
    void discard(BandId band, long id, Instant now);
}
