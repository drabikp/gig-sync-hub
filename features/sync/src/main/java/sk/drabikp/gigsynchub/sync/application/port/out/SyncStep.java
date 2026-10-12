package sk.drabikp.gigsynchub.sync.application.port.out;

import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.gig.domain.Gig;
import sk.drabikp.gigsynchub.gig.domain.platform.Platform;
import sk.drabikp.gigsynchub.sync.domain.StepOutcome;
import sk.drabikp.gigsynchub.sync.domain.StepType;

import java.util.List;
import java.util.Optional;

/**
 * One platform's way of doing one {@link StepType} of a sync workflow (a bulk edit through an
 * upload, an edit through a form). Which steps a platform implements IS what it can do — there
 * is no other declaration and no configuration. At most one per platform and type.
 */
public interface SyncStep {

    Platform platform();

    StepType type();

    /** How many gigs one {@link #run} takes: 1 = one at a time (each result is saved before the next). */
    default int batchSize() {
        return 1;
    }

    /**
     * Why this step can't do the gig — it goes on to the next step — or empty. Asked when the
     * work is queued (so the user hears at once what can't be done) and again before it runs.
     * Takes every gig unless a step says otherwise.
     */
    default Optional<String> refusal(Gig gig) {
        return Optional.empty();
    }

    /**
     * Does the work; one outcome per item, in the same order. Never throws for one gig. The items of
     * one run are always one band's (its platform account does them).
     */
    List<StepOutcome> run(List<Item> items);

    /** A band's gig and the platform's id for its copy. */
    record Item(BandId band, String externalRef, Gig gig) {
    }
}
