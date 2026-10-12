package sk.drabikp.gigsynchub.sync.application.engine;

import org.jmolecules.architecture.hexagonal.Application;
import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.gig.domain.platform.Platforms;
import sk.drabikp.gigsynchub.sync.application.port.in.DispatchSyncUseCase;
import sk.drabikp.gigsynchub.sync.application.port.in.PauseSyncUseCase;
import sk.drabikp.gigsynchub.sync.application.port.out.SyncNotifier;
import sk.drabikp.gigsynchub.sync.application.port.out.SyncOutbox;
import sk.drabikp.gigsynchub.sync.domain.Lane;
import sk.drabikp.gigsynchub.sync.domain.SyncTask;
import sk.drabikp.gigsynchub.sync.domain.Workload;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * Drives the sync outbox for the background workers (several may call {@link #runNext} at once). The next
 * due task — the bands taking turns ({@link FairTurns}), one workload per lane (a band's account on a platform)
 * at a time, none of a paused band or a lane held back by its breaker — says which {@link Workload} runs:
 * that band's work of that action on that platform, all of it, which the {@link WorkflowEngine} then takes
 * through its workflow. A task is never run on its own, because a task cannot say which batch it belongs in.
 *
 * <p>Each task works from the state at the time it RUNS, so whatever changed since it was queued is what
 * reaches the platform. Failures are retried or left for the user by {@link SyncRetryPolicy}.
 */
@Application
public class SyncDispatcher implements DispatchSyncUseCase {

    private final WorkflowEngine engine;
    private final SyncOutbox outbox;
    private final SyncNotifier notifier;
    private final Clock clock;
    private final PauseSyncUseCase pause;
    private final PlatformHealth health;
    private final Platforms platforms;
    private final FairTurns turns;

    public SyncDispatcher(WorkflowEngine engine, SyncOutbox outbox, SyncNotifier notifier, Clock clock,
                          PauseSyncUseCase pause, PlatformHealth health, Platforms platforms,
                          FairTurns turns) {
        this.engine = engine;
        this.outbox = outbox;
        this.notifier = notifier;
        this.clock = clock;
        this.pause = pause;
        this.health = health;
        this.platforms = platforms;
        this.turns = turns;
    }

    @Override
    public boolean runNext() {
        Set<BandId> paused = pause.pausedBands();
        Optional<SyncTask> next = turns.claim(outbox.due(clock.instant()),
                task -> !paused.contains(task.band()) && !health.held(Lane.of(task)));
        if (next.isEmpty()) {
            return false;
        }
        try {
            engine.run(Workload.of(next.get()));
        } finally {
            turns.release(Lane.of(next.get()));
        }
        return true;
    }

    @Override
    public void recoverInterrupted() {
        Instant now = clock.instant();
        for (SyncTask task : outbox.running()) {
            if (task.action().repeatable()) {
                outbox.markRetry(task.id(), "interrupted by a restart — running it again", now, now);
            } else {
                outbox.markFailed(task.id(), "interrupted by a restart — it may or may not have happened on "
                        + platforms.name(task.platform()) + "; check there, then Retry or Discard", now);
            }
            notifier.changed(task.band());
        }
    }
}
