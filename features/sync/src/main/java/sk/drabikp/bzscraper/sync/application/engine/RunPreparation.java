package sk.drabikp.bzscraper.sync.application.engine;

import sk.drabikp.bzscraper.gig.application.port.out.GigRepository;
import sk.drabikp.bzscraper.gig.application.port.out.PublishedGigStore;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.gig.domain.platform.Platforms;
import sk.drabikp.bzscraper.sync.application.port.out.SyncOutbox;
import sk.drabikp.bzscraper.sync.application.port.out.SyncStep;
import sk.drabikp.bzscraper.sync.domain.MootWork;
import sk.drabikp.bzscraper.sync.domain.StepType;
import sk.drabikp.bzscraper.sync.domain.SyncAction;
import sk.drabikp.bzscraper.sync.domain.SyncTask;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Checks a run before its step ({@link WorkflowEngine}): whether its work is still needed (the
 * gig as it is now — moot work ends done, {@link MootWork}), whether the platform's copy is known,
 * and whether this step still takes the gig — otherwise the run moves on or ends here.
 */
public class RunPreparation {

    private final StepRegistry steps;
    private final GigRepository gigRepository;
    private final PublishedGigStore publishedGigStore;
    private final SyncOutbox outbox;
    private final Platforms platforms;
    private final Clock clock;

    public RunPreparation(StepRegistry steps, GigRepository gigRepository, PublishedGigStore publishedGigStore,
                          SyncOutbox outbox, Platforms platforms, Clock clock) {
        this.steps = steps;
        this.gigRepository = gigRepository;
        this.publishedGigStore = publishedGigStore;
        this.outbox = outbox;
        this.platforms = platforms;
        this.clock = clock;
    }

    /** What the step is to do for the run, or empty when the run ended or moved on to another step. */
    Optional<SyncStep.Item> prepare(SyncTask run, StepType stepType) {
        Instant now = clock.instant();
        Platform platform = run.platform();
        Gig gig = gigRepository.findById(run.band(), run.gigId()).orElse(null);
        String ref = publishedGigStore.externalRef(run.band(), platform, run.gigId()).orElse(null);
        Optional<String> moot = MootWork.reason(run.action(), gig,
                publishedGigStore.isPublished(run.band(), platform, run.gigId()), platforms.name(platform));
        if (moot.isPresent()) {
            outbox.markDone(run.id(), moot.get(), now);
            return Optional.empty();
        }
        if (run.action() != SyncAction.PUBLISH && ref == null) {
            outbox.markFailed(run.id(), "no " + platforms.name(platform) + " id was recorded — do it there by hand", now);
            return Optional.empty();
        }
        List<String> reasons = new ArrayList<>();
        Optional<StepType> where = steps.firstTaking(platform, stepType, gig, reasons);
        if (where.isEmpty()) {
            outbox.markFailed(run.id(), steps.byHand(platform, reasons), now);
            return Optional.empty();
        }
        if (where.get() != stepType) {
            outbox.advance(run.id(), where.get(), String.join("; ", reasons), now);
            return Optional.empty();
        }
        return Optional.of(new SyncStep.Item(run.band(), ref, gig));
    }
}
