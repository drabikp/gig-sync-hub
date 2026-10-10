package sk.drabikp.bzscraper.sync.application.engine;

import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.sync.application.port.in.PauseSyncUseCase;
import sk.drabikp.bzscraper.sync.application.port.out.SyncFaults;
import sk.drabikp.bzscraper.sync.application.port.out.SyncNotifier;
import sk.drabikp.bzscraper.sync.application.port.out.SyncOutbox;
import sk.drabikp.bzscraper.sync.application.port.out.SyncStep;
import sk.drabikp.bzscraper.sync.domain.StepOutcome;
import sk.drabikp.bzscraper.sync.domain.StepType;
import sk.drabikp.bzscraper.sync.domain.SyncTask;
import sk.drabikp.bzscraper.sync.domain.Workflows;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Runs sync tasks as workflows ({@link Workflows}): each task is a run that goes through its
 * action's steps in order, using the steps its platform provides ({@link StepRegistry}) — see
 * docs/sync-workflow-plan.md.
 * <ul>
 *   <li>The due runs at one step on one platform go together: a batch step gets up to its
 *       batch size in one call; a one-at-a-time step up to {@value #SINGLE_PER_PASS} gigs,
 *       each result saved before the next, then other work gets its turn.</li>
 *   <li>Before a run: {@link RunPreparation} (moot work ends done; a step that no longer takes
 *       the gig passes it on). After it: {@link OutcomeSettlement}.</li>
 *   <li>A step that throws instead of answering fails its runs (the fault goes to
 *       {@link SyncFaults} in full).</li>
 *   <li>Each batch counts for or against its platform's health; paused or held back, no
 *       further batch starts.</li>
 * </ul>
 */
public class WorkflowEngine {

    static final int SINGLE_PER_PASS = 10;

    private final StepRegistry steps;
    private final RunPreparation preparation;
    private final OutcomeSettlement settlement;
    private final SyncOutbox outbox;
    private final SyncNotifier notifier;
    private final SyncFaults faults;
    private final Clock clock;
    private final PauseSyncUseCase pause;
    private final PlatformHealth health;

    public WorkflowEngine(StepRegistry steps, RunPreparation preparation, OutcomeSettlement settlement,
                          SyncOutbox outbox, SyncNotifier notifier, SyncFaults faults, Clock clock,
                          PauseSyncUseCase pause, PlatformHealth health) {
        this.steps = steps;
        this.preparation = preparation;
        this.settlement = settlement;
        this.outbox = outbox;
        this.notifier = notifier;
        this.faults = faults;
        this.clock = clock;
        this.pause = pause;
        this.health = health;
    }

    /**
     * Runs the work due at {@code task}'s step on its platform — {@code task} and its band's runs waiting
     * with it (a run is never two bands' work).
     */
    public void runFrom(SyncTask task) {
        BandId band = task.band();
        Platform platform = task.platform();
        StepType stepType = StepRegistry.stepOf(task);
        SyncStep step = steps.step(platform, stepType);
        List<SyncTask> waiting = outbox.due(band, platform, task.action(), clock.instant()).stream()
                .filter(t -> StepRegistry.stepOf(t) == stepType).toList();
        if (step == null) {
            // the platform has no such step (anymore): preparing each run moves it on or ends it
            waiting.forEach(run -> preparation.prepare(run, stepType));
            notifier.changed(band);
            return;
        }
        int limit = step.batchSize() == 1 ? SINGLE_PER_PASS : step.batchSize();
        List<SyncTask> runs = new ArrayList<>();
        List<SyncStep.Item> items = new ArrayList<>();
        for (SyncTask run : waiting.subList(0, Math.min(limit, waiting.size()))) {
            Optional<SyncStep.Item> item = preparation.prepare(run, stepType);
            if (item.isPresent()) {
                runs.add(run);
                items.add(item.get());
            }
        }
        for (int from = 0; from < runs.size() && !pause.paused(band) && !health.held(platform); from += step.batchSize()) {
            int to = Math.min(runs.size(), from + step.batchSize());
            runBatch(band, step, runs.subList(from, to), items.subList(from, to));
        }
        notifier.changed(band);
    }

    private void runBatch(BandId band, SyncStep step, List<SyncTask> runs, List<SyncStep.Item> items) {
        List<SyncTask> started = new ArrayList<>();
        List<SyncStep.Item> startedItems = new ArrayList<>();
        for (int i = 0; i < runs.size(); i++) {
            if (outbox.markRunning(runs.get(i).id(), clock.instant())) {    // still PENDING (not discarded)
                started.add(runs.get(i));
                startedItems.add(items.get(i));
            }
        }
        if (started.isEmpty()) {
            return;
        }
        notifier.changed(band);
        List<StepOutcome> outcomes = run(step, startedItems);
        List<StepOutcome> settled = new ArrayList<>();
        for (int i = 0; i < started.size(); i++) {
            StepOutcome outcome = i < outcomes.size() ? outcomes.get(i)
                    : StepOutcome.failed(step.type().label() + " gave no result for this gig");
            settlement.settle(started.get(i), step, startedItems.get(i).gig(), outcome);
            settled.add(outcome);
        }
        // the platform answered if anything but a temporary failure came back; postponed work
        // never reached it
        List<StepOutcome> reached = settled.stream().filter(o -> o.kind() != StepOutcome.Kind.POSTPONED).toList();
        if (!reached.isEmpty() && reached.stream().allMatch(o -> o.kind() == StepOutcome.Kind.FAILED)) {
            health.failed(step.platform(), step.type().label() + ": " + reached.getFirst().note());
        } else if (!reached.isEmpty()) {
            health.succeeded(step.platform());
        }
        notifier.changed(band);
    }

    /** The step's outcomes; a step that throws fails every item — what was thrown is kept for the user and in full. */
    private List<StepOutcome> run(SyncStep step, List<SyncStep.Item> items) {
        try {
            return step.run(items);
        } catch (RuntimeException e) {
            faults.unexpected(step, e);
            String failure = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
            return items.stream().map(item -> StepOutcome.failed(failure)).toList();
        }
    }
}
