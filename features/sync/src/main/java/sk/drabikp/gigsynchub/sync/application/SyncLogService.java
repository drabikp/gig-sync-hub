package sk.drabikp.gigsynchub.sync.application;

import org.jmolecules.architecture.hexagonal.Application;
import sk.drabikp.gigsynchub.gig.application.NotFoundException;
import sk.drabikp.gigsynchub.gig.application.UserFacingException;
import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.gig.domain.GigId;
import sk.drabikp.gigsynchub.sync.application.port.in.SyncErrorCode;
import sk.drabikp.gigsynchub.sync.application.port.in.SyncLogUseCase;
import sk.drabikp.gigsynchub.sync.application.port.out.SyncNotifier;
import sk.drabikp.gigsynchub.sync.application.port.out.SyncOutbox;
import sk.drabikp.gigsynchub.sync.application.port.out.SyncTrigger;
import sk.drabikp.gigsynchub.sync.domain.SyncCounts;
import sk.drabikp.gigsynchub.sync.domain.SyncLogEntry;
import sk.drabikp.gigsynchub.sync.domain.SyncStatus;
import sk.drabikp.gigsynchub.sync.domain.SyncTask;

import java.time.Clock;
import java.util.List;
import java.util.Map;

/** The sync log for the user: reading it, and retrying or discarding what the worker gave up on. */
@Application
public class SyncLogService implements SyncLogUseCase {

    /** How many of the latest tasks "all" shows. */
    static final int LATEST = 500;

    private final SyncOutbox outbox;
    private final SyncTrigger trigger;
    private final SyncNotifier notifier;
    private final Clock clock;

    public SyncLogService(SyncOutbox outbox, SyncTrigger trigger, SyncNotifier notifier, Clock clock) {
        this.outbox = outbox;
        this.trigger = trigger;
        this.notifier = notifier;
        this.clock = clock;
    }

    @Override
    public List<SyncTask> unfinished(BandId band) {
        return outbox.unfinished(band);
    }

    @Override
    public List<SyncTask> history(BandId band, GigId gigId, int limit) {
        return outbox.history(band, gigId, limit);
    }

    @Override
    public List<SyncTask> tasks(BandId band, Show show) {
        // Every list reads the same way: what happened last, first (SyncTask.LATEST_FIRST). The outbox
        // hands its unfinished work over in the order it was asked for, which is the queue's business,
        // not the log's.
        return switch (show) {
            case ALL -> outbox.recent(band, LATEST);
            case OPEN -> outbox.unfinished(band).stream().sorted(SyncTask.LATEST_FIRST).toList();
            case FAILED -> outbox.unfinished(band).stream().filter(t -> t.status() == SyncStatus.FAILED)
                    .sorted(SyncTask.LATEST_FIRST).toList();
        };
    }

    @Override
    public SyncCounts counts(BandId band) {
        return SyncCounts.of(outbox.unfinished(band));
    }

    @Override
    public List<SyncLogEntry> log(BandId band, long taskId) {
        task(band, taskId);
        return outbox.log(taskId);
    }

    @Override
    public void retry(BandId band, long taskId) {
        SyncTask task = task(band, taskId);
        if (task.status() != SyncStatus.FAILED && task.status() != SyncStatus.DISCARDED) {
            throw movedOn(task, "Only a failed or discarded task can be retried — it has moved on meanwhile.");
        }
        outbox.requeue(band, taskId, clock.instant());
        trigger.wake();
        notifier.changed(band);
    }

    @Override
    public void discard(BandId band, long taskId) {
        SyncTask task = task(band, taskId);
        if (task.status() != SyncStatus.FAILED && task.status() != SyncStatus.PENDING) {
            throw movedOn(task, "Only a waiting or failed task can be discarded — it has moved on meanwhile.");
        }
        outbox.discard(band, taskId, clock.instant());
        notifier.changed(band);
    }

    /**
     * The band's task — another band's is "not there" for it. The band is part of the ASKING now;
     * it used to be a filter applied to whatever came back by id, which worked but was a check the
     * build could not see, and the only one standing between a crafted task id and another band's
     * work.
     */
    private SyncTask task(BandId band, long taskId) {
        return outbox.find(band, taskId)
                .orElseThrow(() -> new NotFoundException("no sync task " + taskId));
    }

    /** The task is no longer where the user saw it; {@code status} is where it is now. */
    private static UserFacingException movedOn(SyncTask task, String message) {
        return new UserFacingException(SyncErrorCode.TASK_MOVED_ON,
                Map.of("gig", task.gigLabel(), "status", task.status().name()), message);
    }
}
