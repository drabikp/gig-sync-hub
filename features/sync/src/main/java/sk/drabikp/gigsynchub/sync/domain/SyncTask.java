package sk.drabikp.gigsynchub.sync.domain;

import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;
import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.gig.domain.Gig;
import sk.drabikp.gigsynchub.gig.domain.GigId;
import sk.drabikp.gigsynchub.gig.domain.platform.Platform;

import java.time.Instant;
import java.util.Comparator;

/**
 * One entry of the sync outbox: bring a band's gig's copy on one platform in line with the
 * band's catalog. Written in the same transaction as the catalog change that needs it, run
 * later by the dispatcher. {@code gigLabel} keeps the gig readable in the log after the
 * gig itself is gone. {@code nextAttemptAt} is when a PENDING task may run;
 * {@code message} is the latest outcome or error. {@code step} is how far its workflow got
 * ({@link #waitingAt()}; null: not started) — written down each time the run moves on so that a
 * restart, or the next turn this band's lane gets, carries on from there rather than from the
 * beginning. It is a note of where the run stands, not what decides where it goes next: the
 * engine decides that, and sees the whole workflow while it does.
 */
@AggregateRoot
public record SyncTask(@Identity long id, BandId band, GigId gigId, String gigLabel, Platform platform, SyncAction action,
                       SyncStatus status, int attempts, Instant createdAt, Instant nextAttemptAt,
                       Instant updatedAt, String message, StepType step) {

    /**
     * How a log of activity reads: what happened LAST comes first, by {@code updatedAt} — not by id,
     * which is the order the work was asked for. The two differ as soon as anything is retried or
     * simply takes longer than what was queued after it, and the page shows each row's
     * {@code updatedAt}, so by id it shows plainly wrong times. The id settles a tie (same instant).
     */
    public static final Comparator<SyncTask> LATEST_FIRST =
            Comparator.comparing(SyncTask::updatedAt).thenComparingLong(SyncTask::id).reversed();

    /**
     * The step of its workflow this task waits at: where its run got to, or the workflow's first
     * step while it has not started. The engine reads it to know where to take the run up again —
     * after a restart, or simply when its turn comes round.
     */
    public StepType waitingAt() {
        return step != null ? step : Workflows.of(action).getFirst();
    }

    /** How a gig is named in the sync log: "2026-09-18 · Eufory + Snaefell, Hranice". */
    public static String labelOf(Gig gig) {
        return gig.schedule().startDate() + " · " + gig.title() + ", " + gig.location().city();
    }

    /** Waiting for a retry (it ran before), rather than for its first run. */
    public boolean retrying() {
        return status == SyncStatus.PENDING && attempts > 0;
    }
}
