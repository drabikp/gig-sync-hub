package sk.drabikp.gigsynchub.sync.application.engine;

import org.jmolecules.architecture.hexagonal.Application;
import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.gig.domain.platform.Platforms;
import sk.drabikp.gigsynchub.sync.application.port.in.PauseSyncUseCase;
import sk.drabikp.gigsynchub.sync.application.port.out.SyncFaults;
import sk.drabikp.gigsynchub.sync.application.port.out.SyncNotifier;
import sk.drabikp.gigsynchub.sync.application.port.out.SyncOutbox;
import sk.drabikp.gigsynchub.sync.application.port.out.SyncStep;
import sk.drabikp.gigsynchub.sync.domain.Lane;
import sk.drabikp.gigsynchub.sync.domain.StepOutcome;
import sk.drabikp.gigsynchub.sync.domain.StepType;
import sk.drabikp.gigsynchub.sync.domain.SyncTask;
import sk.drabikp.gigsynchub.sync.domain.Workflows;
import sk.drabikp.gigsynchub.sync.domain.Workload;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Runs a {@link Workload} — a band's due work of one action on one platform — through that
 * action's workflow: the {@link Workflows workflow's steps in order}, each step with the gigs it
 * is to do, using the step implementations the platform provides ({@link StepRegistry}).
 *
 * <p><b>The engine owns the order.</b> One call walks the whole workflow, and a gig a step passes
 * on is tried the next way in the same call, while the platform's session is still open:
 *
 * <pre>
 * run(a band's UPDATE work on a platform that has both of these steps)
 *
 *   bulk edit  ← the due runs waiting here, at most one batch of them
 *                ├─ done, failed for good, out of retries  → settled; it leaves the pass
 *                ├─ failed with a retry left, postponed    → it waits here for another turn
 *                └─ refused, or this step won't take the gig, or the platform hasn't got
 *                   the step at all                        → on to the next step, below
 *
 *   form edit  ← what the bulk edit passed on, and the runs that were already waiting here
 *                └─ nothing left to try  → FAILED: "do it there by hand (every reason)"
 * </pre>
 *
 * <p>{@code sync_task.step} is written down at each hop ({@link SyncOutbox#advance}) so that a
 * restart, or a pass cut short by a pause, carries on from where the run stands. It is a
 * checkpoint, not the thing that drives the run: nothing has to come round again for a workflow
 * to get to its end.
 *
 * <p><b>A pass's share.</b> Each step does at most one batch — a batch step its own batch size
 * (an upload of twenty-five gigs is one call), a one-at-a-time step
 * {@value #ONE_AT_A_TIME_PER_PASS} gigs, each result written down before the next. Then the
 * workload's turn is over and the lane is given up, so the other bands and this band's other
 * platforms get theirs; whatever is left waits where it stands and the next pass takes it up.
 *
 * <p>Nothing new starts while the band is paused or its lane is held back by a breaker — checked
 * between steps and between batches, so the gig in progress finishes and the rest stays queued.
 * Each batch that reached the platform counts for or against {@link PlatformHealth the band's
 * account there}; a step that throws instead of answering fails that batch's gigs and the fault
 * is kept in full ({@link SyncFaults}).
 *
 * <p>See docs/sync-workflow-plan.md.
 */
@Application
public class WorkflowEngine {

    /** How many gigs a one-at-a-time step does in one pass before the workload's turn is over. */
    static final int ONE_AT_A_TIME_PER_PASS = 10;

    private final StepRegistry steps;
    private final RunIntake intake;
    private final OutcomeSettlement settlement;
    private final SyncOutbox outbox;
    private final SyncNotifier notifier;
    private final SyncFaults faults;
    private final Clock clock;
    private final PauseSyncUseCase pause;
    private final PlatformHealth health;
    private final Platforms platforms;

    public WorkflowEngine(StepRegistry steps, RunIntake intake, OutcomeSettlement settlement, SyncOutbox outbox,
                          SyncNotifier notifier, SyncFaults faults, Clock clock, PauseSyncUseCase pause,
                          PlatformHealth health, Platforms platforms) {
        this.steps = steps;
        this.intake = intake;
        this.settlement = settlement;
        this.outbox = outbox;
        this.notifier = notifier;
        this.faults = faults;
        this.clock = clock;
        this.pause = pause;
        this.health = health;
        this.platforms = platforms;
    }

    /** Runs the workload's due work through its workflow, each run from the step it waits at. */
    public void run(Workload workload) {
        Map<StepType, List<Enrolled>> waiting = whatIsDue(workload);
        for (StepType type : Workflows.of(workload.action())) {
            List<Enrolled> here = waiting.getOrDefault(type, List.of());
            if (here.isEmpty()) {
                continue;
            }
            if (!mayRun(workload.lane())) {
                break;                  // paused, or held back: every run waits where it stands
            }
            atStep(workload, type, here, waiting);
        }
        notifier.changed(workload.band());
    }

    /** The workload's due runs, under the step each of them waits at. */
    private Map<StepType, List<Enrolled>> whatIsDue(Workload workload) {
        Map<StepType, List<Enrolled>> waiting = new EnumMap<>(StepType.class);
        for (SyncTask run : outbox.due(workload.band(), workload.platform(), workload.action(), clock.instant())) {
            waiting.computeIfAbsent(run.waitingAt(), step -> new ArrayList<>()).add(new Enrolled(run, List.of()));
        }
        return waiting;
    }

    /**
     * One step of the workflow: this pass's share of the runs waiting here, a batch at a time.
     * Runs this step passes on join the step that is to try next, which this pass has not reached
     * yet.
     *
     * <p><b>Each batch is asked what to do only when its turn comes.</b> A batch step has one
     * batch per pass, so for it this is the same thing either way; a one-at-a-time step has up to
     * {@value #ONE_AT_A_TIME_PER_PASS}, run over the minutes that human pacing takes, and reading
     * all ten gigs up front meant the tenth was told to the platform as it stood before the first
     * one ran. Cancel a gig while its pass is going and the step pushed the uncancelled gig and
     * then marked the task done: the catalog said cancelled, the platform said live, and the sync
     * believed it had finished. It is also what made {@code MootWork}'s "already published" guard
     * answer for both rows of a double publish.
     */
    private void atStep(Workload workload, StepType type, List<Enrolled> here,
                        Map<StepType, List<Enrolled>> waiting) {
        SyncStep step = steps.step(workload.platform(), type);
        if (step == null) {
            here.forEach(run -> onward(run, type, "none for " + platforms.name(workload.platform()), waiting));
            return;
        }
        List<Enrolled> share = here.subList(0, Math.min(gigsThisPass(step), here.size()));
        for (int from = 0; from < share.size() && mayRun(workload.lane()); from += step.batchSize()) {
            int to = Math.min(share.size(), from + step.batchSize());
            List<Enrolled> taken = new ArrayList<>();
            List<SyncStep.Item> items = new ArrayList<>();
            for (Enrolled run : share.subList(from, to)) {
                switch (intake.at(step, run.run())) {
                    case Intake.Take(SyncStep.Item item) -> {
                        taken.add(run);
                        items.add(item);
                    }
                    case Intake.Moot(String why) -> outbox.markDone(run.band(), run.id(), why, clock.instant());
                    case Intake.ByHand(String message) -> outbox.markFailed(run.band(), run.id(), message, clock.instant());
                    case Intake.PassedOn(String why) -> onward(run, type, why, waiting);
                }
            }
            if (!taken.isEmpty()) {
                runBatch(workload, step, taken, items, waiting);
            }
        }
    }

    /**
     * One call of the step: the runs still PENDING (the user may have discarded one meanwhile) go
     * to the platform together, each gig's outcome is written down on its own, and the batch as a
     * whole says how the band's account on this platform is answering.
     */
    private void runBatch(Workload workload, SyncStep step, List<Enrolled> batch, List<SyncStep.Item> items,
                          Map<StepType, List<Enrolled>> waiting) {
        List<Enrolled> started = new ArrayList<>();
        List<SyncStep.Item> startedItems = new ArrayList<>();
        for (int i = 0; i < batch.size(); i++) {
            if (outbox.markRunning(batch.get(i).band(), batch.get(i).id(), clock.instant())) {
                started.add(batch.get(i));
                startedItems.add(items.get(i));
            }
        }
        if (started.isEmpty()) {
            return;
        }
        notifier.changed(workload.band());
        List<StepOutcome> outcomes = ask(step, startedItems);
        List<StepOutcome> answers = new ArrayList<>();
        for (int i = 0; i < started.size(); i++) {
            StepOutcome outcome = i < outcomes.size() ? outcomes.get(i)
                    : StepOutcome.failed(step.type().label() + " gave no result for this gig");
            settle(started.get(i), step, outcome, waiting);
            answers.add(outcome);
        }
        report(new Lane(workload.band(), step.platform()), step, answers);
        notifier.changed(workload.band());
    }

    /**
     * This step passed the gig on: the run moves to the next step of its workflow — written down
     * there, and taken up again later in this very pass. When the workflow has nothing left to
     * try, the run waits for the user with every reason it collected on the way.
     */
    private void onward(Enrolled run, StepType from, String why, Map<StepType, List<Enrolled>> waiting) {
        String said = from.label() + ": " + why;
        List<String> reasons = new ArrayList<>(run.reasons());
        reasons.add(said);
        Instant now = clock.instant();
        Optional<StepType> next = Workflows.after(from);
        if (next.isEmpty()) {
            outbox.markFailed(run.band(), run.id(), steps.byHand(run.run().platform(), reasons), now);
            return;
        }
        outbox.advance(run.band(), run.id(), next.get(), said, now);
        outbox.find(run.band(), run.id())           // moving on begins the attempts afresh: take the run as it now is
                .ifPresent(moved -> waiting.computeIfAbsent(next.get(), step -> new ArrayList<>())
                        .add(new Enrolled(moved, reasons)));
    }

    /**
     * One gig's outcome written down, and the run moved on if the step passed it on.
     *
     * <p>Guarded per gig, because this is the one write in a batch that can fail on something
     * other than the platform: an edit during the pass relabels the gig's open work
     * ({@link SyncOutbox#relabel}) and bumps {@code sync_task.version}, so the settling write
     * loses its optimistic check. Unguarded, that exception left the batch — and every gig in it
     * that was already marked RUNNING stayed RUNNING for ever, because only a restart settles
     * those: a spinner on each of their pages and 409 {@code gigBusy} on every action the band
     * tried. The gig in hand is left to the reaper ({@link SyncDispatcher#recoverInterrupted})
     * and its fault kept in full; the rest of the batch is still written down.
     */
    private void settle(Enrolled run, SyncStep step, StepOutcome outcome,
                        Map<StepType, List<Enrolled>> waiting) {
        try {
            if (settlement.settle(run.run(), step, outcome) == OutcomeSettlement.Settled.PASSED_ON) {
                onward(run, step.type(), outcome.note(), waiting);
            }
        } catch (RuntimeException e) {
            faults.unexpected(step, e);
        }
    }

    /** A batch that reached the platform at all tells us whether the band's account there is working. */
    private void report(Lane lane, SyncStep step, List<StepOutcome> outcomes) {
        // postponed work never reached the platform, so it says nothing either way
        List<StepOutcome> reached = outcomes.stream().filter(o -> o.kind() != StepOutcome.Kind.POSTPONED).toList();
        if (reached.isEmpty()) {
            return;
        }
        if (reached.stream().allMatch(o -> o.kind() == StepOutcome.Kind.FAILED)) {
            health.failed(lane, step.type().label() + ": " + reached.getFirst().note());
        } else {
            health.succeeded(lane);
        }
    }

    /** The step's own answers; a step that throws instead of answering fails every gig of the batch. */
    private List<StepOutcome> ask(SyncStep step, List<SyncStep.Item> items) {
        try {
            return step.run(items);
        } catch (RuntimeException e) {
            faults.unexpected(step, e);
            String failure = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
            return items.stream().map(item -> StepOutcome.failed(failure)).toList();
        }
    }

    /**
     * How many of a step's gigs one pass does: a batch step one batch of its own size, a
     * one-at-a-time step {@value #ONE_AT_A_TIME_PER_PASS} — then the lane is given up.
     */
    private static int gigsThisPass(SyncStep step) {
        return step.batchSize() == 1 ? ONE_AT_A_TIME_PER_PASS : step.batchSize();
    }

    /** Nothing new starts while the band is paused or its lane is held back. */
    private boolean mayRun(Lane lane) {
        return !pause.paused(lane.band()) && !health.held(lane);
    }

    /**
     * A run as this pass holds it: the task as it was last read, and what the steps before this
     * one said when they passed the gig on — which is what the user is told if nothing can do it.
     */
    private record Enrolled(SyncTask run, List<String> reasons) {

        long id() {
            return run.id();
        }

        /** Whose work this is — asked for with the id at every write, the id alone naming no band. */
        BandId band() {
            return run.band();
        }
    }
}
