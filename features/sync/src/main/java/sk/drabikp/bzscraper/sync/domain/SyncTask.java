package sk.drabikp.bzscraper.sync.domain;

import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;

import java.time.Instant;
import java.util.Comparator;

/**
 * One entry of the sync outbox: bring a band's gig's copy on one platform in line with the
 * band's catalog. Written in the same transaction as the catalog change that needs it, run
 * later by the dispatcher. {@code gigLabel} keeps the gig readable in the log after the
 * gig itself is gone. {@code nextAttemptAt} is when a PENDING task may run;
 * {@code message} is the latest outcome or error. {@code step} is where a workflow run
 * stands (null: not started — it waits at its workflow's first step).
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

    /** How a gig is named in the sync log: "2026-09-18 · Eufory + Snaefell, Hranice". */
    public static String labelOf(Gig gig) {
        return gig.schedule().startDate() + " · " + gig.title() + ", " + gig.location().city();
    }

    /** Waiting for a retry (it ran before), rather than for its first run. */
    public boolean retrying() {
        return status == SyncStatus.PENDING && attempts > 0;
    }
}
