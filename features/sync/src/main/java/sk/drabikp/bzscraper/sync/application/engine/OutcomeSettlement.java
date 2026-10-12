package sk.drabikp.bzscraper.sync.application.engine;

import org.jmolecules.architecture.hexagonal.Application;
import sk.drabikp.bzscraper.gig.application.port.out.PublishedGigStore;
import sk.drabikp.bzscraper.gig.application.port.out.Transactions;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.gig.domain.platform.Platforms;
import sk.drabikp.bzscraper.sync.application.port.out.SyncOutbox;
import sk.drabikp.bzscraper.sync.application.port.out.SyncStep;
import sk.drabikp.bzscraper.sync.domain.StepOutcome;
import sk.drabikp.bzscraper.sync.domain.SyncAction;
import sk.drabikp.bzscraper.sync.domain.SyncRetryPolicy;
import sk.drabikp.bzscraper.sync.domain.SyncTask;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * What a step's outcome means for its run, written down at once:
 * <ul>
 *   <li><b>done</b> — with the platform record change in the same transaction: a created event's
 *       id recorded, a removed one's forgotten;</li>
 *   <li><b>failed for good</b> — the user decides, nothing else is tried;</li>
 *   <li><b>failed</b> — this step again later, as {@link SyncRetryPolicy} allows, and never
 *       another way: it may have half-happened. A create is not retried by itself at all — it may
 *       have made the event anyway, and only the user can look and say;</li>
 *   <li><b>postponed</b> — the step never reached the platform (the app was stopping): this step
 *       again in a minute, no attempt used.</li>
 * </ul>
 *
 * <p>A <b>refusal</b> is the one outcome nothing is written down for here. The platform said no
 * and nothing happened there, so what comes next is the workflow's next step — which is the
 * {@link WorkflowEngine}'s to decide, and it is told so by {@link Settled#PASSED_ON}.
 */
@Application
public class OutcomeSettlement {

    /** Where the run stands once its outcome has been written down. */
    public enum Settled {

        /** Settled: done, or left for the user. */
        ENDED,

        /** The same step again later — a retry, or a postponement — so not in this pass. */
        WAITING,

        /** The platform said no and nothing happened there: the next way may do it. */
        PASSED_ON
    }

    private final PublishedGigStore publishedGigStore;
    private final SyncOutbox outbox;
    private final Transactions transactions;
    private final Platforms platforms;
    private final Clock clock;

    public OutcomeSettlement(PublishedGigStore publishedGigStore, SyncOutbox outbox, Transactions transactions,
                             Platforms platforms, Clock clock) {
        this.publishedGigStore = publishedGigStore;
        this.outbox = outbox;
        this.transactions = transactions;
        this.platforms = platforms;
        this.clock = clock;
    }

    Settled settle(SyncTask run, SyncStep step, StepOutcome outcome) {
        Instant now = clock.instant();
        String label = step.type().label();
        return switch (outcome.kind()) {
            case DONE -> {
                done(run, label, outcome, now);
                yield Settled.ENDED;
            }
            case FAILED_FOR_GOOD -> {
                outbox.markFailed(run.id(), label + ": " + outcome.note(), now);
                yield Settled.ENDED;
            }
            case POSTPONED -> {
                outbox.postpone(run.id(), label + ": " + outcome.note(), now.plus(SyncRetryPolicy.POSTPONE), now);
                yield Settled.WAITING;
            }
            case FAILED -> failed(run, label, outcome, now);
            case REFUSED -> Settled.PASSED_ON;
        };
    }

    private void done(SyncTask run, String label, StepOutcome outcome, Instant now) {
        transactions.inTransaction(() -> {
            GigId gigId = run.gigId();
            if (outcome.ref() != null) {
                publishedGigStore.record(run.band(), run.platform(), gigId, outcome.ref());
            }
            if (run.action() == SyncAction.DELETE) {
                publishedGigStore.remove(run.band(), run.platform(), gigId);
            }
            outbox.markDone(run.id(),
                    label + (outcome.note() == null || outcome.note().isBlank() ? "" : ": " + outcome.note()), now);
        });
    }

    private Settled failed(SyncTask run, String label, StepOutcome outcome, Instant now) {
        Optional<Instant> retryAt = SyncRetryPolicy.nextAttempt(run.action(), run.attempts() + 1, now);
        if (retryAt.isPresent()) {
            outbox.markRetry(run.id(), label + ": " + outcome.note(), retryAt.get(), now);
            return Settled.WAITING;
        }
        outbox.markFailed(run.id(), label + ": " + outcome.note()
                + (run.action().repeatable() ? "" : " — check " + platforms.name(run.platform())
                + ", then Retry or Discard"), now);
        return Settled.ENDED;
    }
}
