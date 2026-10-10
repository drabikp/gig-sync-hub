package sk.drabikp.bzscraper.sync.application.engine;

import sk.drabikp.bzscraper.gig.domain.platform.Platforms;
import sk.drabikp.bzscraper.sync.application.port.in.DispatchSyncUseCase;
import sk.drabikp.bzscraper.sync.application.port.in.PauseSyncUseCase;
import sk.drabikp.bzscraper.sync.application.port.out.SyncNotifier;
import sk.drabikp.bzscraper.sync.application.port.out.SyncOutbox;
import sk.drabikp.bzscraper.sync.domain.SyncTask;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Drives the sync outbox for the background worker: the next due task goes to the
 * {@link WorkflowEngine}, which runs it — with the runs waiting at the same step on the
 * same platform — through the platform's steps. Each task works from the state at the time
 * it RUNS, so whatever changed since it was queued is what reaches the platform. Failures
 * are retried or left for the user by {@link SyncRetryPolicy}.
 */
public class SyncDispatcher implements DispatchSyncUseCase {

    private final WorkflowEngine engine;
    private final SyncOutbox outbox;
    private final SyncNotifier notifier;
    private final Clock clock;
    private final PauseSyncUseCase pause;
    private final PlatformHealth health;
    private final Platforms platforms;

    public SyncDispatcher(WorkflowEngine engine, SyncOutbox outbox, SyncNotifier notifier, Clock clock,
                          PauseSyncUseCase pause, PlatformHealth health, Platforms platforms) {
        this.engine = engine;
        this.outbox = outbox;
        this.notifier = notifier;
        this.clock = clock;
        this.pause = pause;
        this.health = health;
        this.platforms = platforms;
    }

    @Override
    public boolean runNext() {
        Optional<SyncTask> next = outbox.nextDue(clock.instant(), health.held(), pause.pausedBands());
        next.ifPresent(engine::runFrom);
        return next.isPresent();
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
