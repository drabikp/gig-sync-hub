package sk.drabikp.bzscraper.sync.domain;

import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;

import java.time.Instant;

/**
 * One entry of the sync outbox: bring a band's gig's copy on one platform in line with the
 * band's catalog. Written in the same transaction as the catalog change that needs it, run
 * later by the dispatcher. {@code gigLabel} keeps the gig readable in the log after the
 * gig itself is gone. {@code nextAttemptAt} is when a PENDING task may run;
 * {@code message} is the latest outcome or error. {@code step} is where a workflow run
 * stands (null: not started — it waits at its workflow's first step).
 */
public record SyncTask(long id, BandId band, GigId gigId, String gigLabel, Platform platform, SyncAction action,
                       SyncStatus status, int attempts, Instant createdAt, Instant nextAttemptAt,
                       Instant updatedAt, String message, StepType step) {

    /** How a gig is named in the sync log: "2026-09-18 · Eufory + Snaefell, Hranice". */
    public static String labelOf(Gig gig) {
        return gig.schedule().startDate() + " · " + gig.title() + ", " + gig.location().city();
    }

    /** How a gig that is no longer in the catalog is named: "2026-09-18 · klub 007" (its identity). */
    public static String labelOf(GigId id) {
        return id.date() + " · " + (id.venue().startsWith("@") ? id.venue().substring(1) : id.venue());
    }

    /** Waiting for a retry (it ran before), rather than for its first run. */
    public boolean retrying() {
        return status == SyncStatus.PENDING && attempts > 0;
    }
}
