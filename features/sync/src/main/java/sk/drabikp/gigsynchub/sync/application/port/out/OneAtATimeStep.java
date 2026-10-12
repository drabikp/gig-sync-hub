package sk.drabikp.gigsynchub.sync.application.port.out;

import sk.drabikp.gigsynchub.sync.domain.StepOutcome;

import java.util.List;

/**
 * A step that does one gig at a time (a platform's form): each gig gets its own outcome, so
 * the engine saves each before the next.
 */
public interface OneAtATimeStep extends SyncStep {

    /** Does the step for one gig; a failure is its outcome, never thrown. */
    StepOutcome runOne(Item item);

    @Override
    default int batchSize() {
        return 1;
    }

    @Override
    default List<StepOutcome> run(List<Item> items) {
        return items.stream().map(this::runOne).toList();
    }
}
