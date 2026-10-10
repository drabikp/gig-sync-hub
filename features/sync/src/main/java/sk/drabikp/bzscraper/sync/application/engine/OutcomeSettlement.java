package sk.drabikp.bzscraper.sync.application.engine;

import sk.drabikp.bzscraper.gig.application.port.out.PublishedGigStore;
import sk.drabikp.bzscraper.gig.application.port.out.Transactions;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.gig.domain.platform.Platforms;
import sk.drabikp.bzscraper.sync.application.port.out.SyncOutbox;
import sk.drabikp.bzscraper.sync.application.port.out.SyncStep;
import sk.drabikp.bzscraper.sync.domain.StepOutcome;
import sk.drabikp.bzscraper.sync.domain.StepType;
import sk.drabikp.bzscraper.sync.domain.SyncAction;
import sk.drabikp.bzscraper.sync.domain.SyncRetryPolicy;
import sk.drabikp.bzscraper.sync.domain.SyncTask;
import sk.drabikp.bzscraper.sync.domain.Workflows;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What a step's outcome means for its run ({@link WorkflowEngine}): done (with the platform
 * record change — a created event's id recorded, a removed one's forgotten — in the same
 * transaction); refused (nothing happened there) → the next step that takes the gig; failed →
 * this step again later, as {@link SyncRetryPolicy} allows (never for creates: one may have
 * happened anyway); failed for good → the user; postponed (the platform's browser was busy) →
 * this step again in a minute.
 */
public class OutcomeSettlement {

    private final StepRegistry steps;
    private final PublishedGigStore publishedGigStore;
    private final SyncOutbox outbox;
    private final Transactions transactions;
    private final Platforms platforms;
    private final Clock clock;

    public OutcomeSettlement(StepRegistry steps, PublishedGigStore publishedGigStore, SyncOutbox outbox,
                             Transactions transactions, Platforms platforms, Clock clock) {
        this.steps = steps;
        this.publishedGigStore = publishedGigStore;
        this.outbox = outbox;
        this.transactions = transactions;
        this.platforms = platforms;
        this.clock = clock;
    }

    void settle(SyncTask run, SyncStep step, Gig gig, StepOutcome outcome) {
        Instant now = clock.instant();
        String label = step.type().label();
        switch (outcome.kind()) {
            case DONE -> done(run, label, outcome, now);
            case FAILED_FOR_GOOD -> outbox.markFailed(run.id(), label + ": " + outcome.note(), now);
            case POSTPONED -> outbox.postpone(run.id(), label + ": " + outcome.note(),
                    now.plus(SyncRetryPolicy.POSTPONE), now);
            case FAILED -> failed(run, label, outcome, now);
            case REFUSED -> refused(run, step, gig, label, outcome, now);
        }
    }

    private void done(SyncTask run, String label, StepOutcome outcome, Instant now) {
        transactions.inTransaction(() -> {
            // the gig's identity may have moved while the step ran: record under the current one
            GigId gigId = outbox.find(run.id()).map(SyncTask::gigId).orElse(run.gigId());
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

    private void failed(SyncTask run, String label, StepOutcome outcome, Instant now) {
        Optional<Instant> retryAt = SyncRetryPolicy.nextAttempt(run.action(), run.attempts() + 1, now);
        if (retryAt.isPresent()) {
            outbox.markRetry(run.id(), label + ": " + outcome.note(), retryAt.get(), now);
        } else {
            outbox.markFailed(run.id(), label + ": " + outcome.note()
                    + (run.action().repeatable() ? "" : " — check " + platforms.name(run.platform())
                    + ", then Retry or Discard"), now);
        }
    }

    /** The platform said no and nothing happened there: another way may do it. */
    private void refused(SyncTask run, SyncStep step, Gig gig, String label, StepOutcome outcome, Instant now) {
        List<String> reasons = new ArrayList<>(List.of(label + ": " + outcome.note()));
        Optional<StepType> next = Workflows.after(step.type())
                .flatMap(after -> steps.firstTaking(run.platform(), after, gig, reasons));
        if (next.isPresent()) {
            outbox.advance(run.id(), next.get(), String.join("; ", reasons), now);
        } else {
            outbox.markFailed(run.id(), steps.byHand(run.platform(), reasons), now);
        }
    }
}
