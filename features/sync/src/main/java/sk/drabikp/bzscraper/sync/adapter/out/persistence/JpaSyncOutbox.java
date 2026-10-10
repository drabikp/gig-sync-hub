package sk.drabikp.bzscraper.sync.adapter.out.persistence;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.sync.application.port.out.SyncOutbox;
import sk.drabikp.bzscraper.sync.domain.StepType;
import sk.drabikp.bzscraper.sync.domain.SyncAction;
import sk.drabikp.bzscraper.sync.domain.SyncLogEntry;
import sk.drabikp.bzscraper.sync.domain.SyncStatus;
import sk.drabikp.bzscraper.sync.domain.SyncTask;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * JPA-backed {@link SyncOutbox} (tables {@code sync_task} + {@code sync_log}) in the
 * catalog's database: enqueueing joins the caller's transaction, which is what makes it
 * an outbox. Every state change writes a log line.
 */
@Component
@Transactional(readOnly = true)
class JpaSyncOutbox implements SyncOutbox {

    private static final List<String> OPEN = List.of(SyncStatus.PENDING.name(), SyncStatus.RUNNING.name());
    private static final List<String> UNFINISHED =
            List.of(SyncStatus.PENDING.name(), SyncStatus.RUNNING.name(), SyncStatus.FAILED.name());

    private final SyncTaskJpaRepository tasks;
    private final SyncLogJpaRepository log;
    private final SyncEntityMapper mapper;

    public JpaSyncOutbox(SyncTaskJpaRepository tasks, SyncLogJpaRepository log, SyncEntityMapper mapper) {
        this.tasks = tasks;
        this.log = log;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public SyncTask enqueue(BandId band, GigId gigId, String gigLabel, Platform platform, SyncAction action,
                            Instant now) {
        SyncTaskEntity task = tasks.save(new SyncTaskEntity(band.value(), gigId.key(), gigLabel, platform,
                action, now));
        write(task, now, "queued");
        return mapper.toTask(task);
    }

    @Override
    public Optional<SyncTask> find(long id) {
        return tasks.findById(id).map(mapper::toTask);
    }

    @Override
    public List<SyncTask> openFor(BandId band, GigId gigId) {
        return mapper.toTasks(tasks.findByBandIdAndGigIdAndStatusInOrderByIdAsc(band.value(), gigId.key(), OPEN));
    }

    @Override
    public List<SyncTask> unfinished(BandId band) {
        return mapper.toTasks(tasks.findByBandIdAndStatusInOrderByIdAsc(band.value(), UNFINISHED));
    }

    @Override
    public List<SyncTask> recent(BandId band, int limit) {
        return mapper.toTasks(tasks.findByBandIdOrderByIdDesc(band.value(), Limit.of(limit)));
    }

    @Override
    public List<SyncTask> history(BandId band, GigId gigId, int limit) {
        return mapper.toTasks(tasks.findByBandIdAndGigIdOrderByIdDesc(band.value(), gigId.key(), Limit.of(limit)));
    }

    @Override
    public List<SyncLogEntry> log(long taskId) {
        return mapper.toEntries(log.findByTaskIdOrderByIdAsc(taskId));
    }

    @Override
    @Transactional
    public List<SyncTask> supersede(BandId band, GigId gigId, Platform platform, String why, Instant now) {
        List<SyncTaskEntity> pending = tasks.findByBandIdAndGigIdAndPlatformAndStatusOrderByIdAsc(
                band.value(), gigId.key(), platform.id(), SyncStatus.PENDING.name());
        for (SyncTaskEntity task : pending) {
            task.change(SyncStatus.DISCARDED, null, "superseded: " + why, now);
            write(task, now, "superseded: " + why);
        }
        return mapper.toTasks(pending);
    }

    @Override
    @Transactional
    public List<SyncTask> replaceFailed(BandId band, GigId gigId, Platform platform, Set<SyncAction> actions,
                                        String why, Instant now) {
        List<SyncTaskEntity> failed = tasks.findByBandIdAndGigIdAndPlatformAndStatusOrderByIdAsc(
                        band.value(), gigId.key(), platform.id(), SyncStatus.FAILED.name()).stream()
                .filter(task -> actions.contains(SyncAction.valueOf(task.getAction())))
                .toList();
        for (SyncTaskEntity task : failed) {
            task.change(SyncStatus.DISCARDED, null, "replaced: " + why, now);
            write(task, now, "replaced: " + why);
        }
        return mapper.toTasks(failed);
    }

    @Override
    @Transactional
    public void move(BandId band, GigId from, GigId to, String newLabel) {
        for (SyncTaskEntity task : tasks.findByBandIdAndGigId(band.value(), from.key())) {
            task.moveTo(to.key(), newLabel);
        }
    }

    @Override
    public Optional<SyncTask> nextDue(Instant now) {
        return tasks.findFirstByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(SyncStatus.PENDING.name(), now)
                .map(mapper::toTask);
    }

    @Override
    public List<SyncTask> due(Instant now) {
        return mapper.toTasks(tasks.findByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(SyncStatus.PENDING.name(), now));
    }

    @Override
    public List<SyncTask> due(BandId band, Platform platform, SyncAction action, Instant now) {
        return mapper.toTasks(tasks.findByBandIdAndPlatformAndActionAndStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
                band.value(), platform.id(), action.name(), SyncStatus.PENDING.name(), now));
    }

    @Override
    public List<SyncTask> running() {
        return mapper.toTasks(tasks.findByStatusInOrderByIdAsc(List.of(SyncStatus.RUNNING.name())));
    }

    @Override
    @Transactional
    public boolean markRunning(long id, Instant now) {
        SyncTaskEntity task = get(id);
        if (task.status() != SyncStatus.PENDING) {
            return false;
        }
        task.startAttempt(now);
        write(task, now, "attempt " + task.getAttempts() + " started");
        return true;
    }

    @Override
    @Transactional
    public void markDone(long id, String message, Instant now) {
        SyncTaskEntity task = get(id);
        if (task.change(SyncStatus.DONE, null, message, now)) {
            write(task, now, message == null || message.isBlank() ? "done" : "done — " + message);
        }
    }

    @Override
    @Transactional
    public void markRetry(long id, String error, Instant nextAttempt, Instant now) {
        SyncTaskEntity task = get(id);
        if (task.change(SyncStatus.PENDING, nextAttempt, error, now)) {
            write(task, now, "failed: " + error + " — will retry at " + nextAttempt);
        }
    }

    @Override
    @Transactional
    public void markFailed(long id, String error, Instant now) {
        SyncTaskEntity task = get(id);
        if (task.change(SyncStatus.FAILED, null, error, now)) {
            write(task, now, "failed: " + error + " — waiting for you to Retry or Discard");
        }
    }

    @Override
    @Transactional
    public void postpone(long id, String why, Instant nextAttempt, Instant now) {
        SyncTaskEntity task = get(id);
        if (task.status() == SyncStatus.RUNNING && task.change(SyncStatus.PENDING, nextAttempt, why, now)) {
            task.undoAttempt();
            write(task, now, "postponed: " + why + " — again at " + nextAttempt);
        }
    }

    @Override
    @Transactional
    public void advance(long id, StepType next, String why, Instant now) {
        SyncTaskEntity task = get(id);
        if (task.advance(next, now)) {
            write(task, now, why + " → " + next.label());
        }
    }

    @Override
    @Transactional
    public void requeue(long id, Instant now) {
        SyncTaskEntity task = get(id);
        if (task.change(SyncStatus.PENDING, now, null, now)) {
            task.resetAttempts();
            write(task, now, "retried by you");
        }
    }

    @Override
    @Transactional
    public void discard(long id, Instant now) {
        SyncTaskEntity task = get(id);
        if (task.change(SyncStatus.DISCARDED, null, "discarded by you", now)) {
            write(task, now, "discarded by you");
        }
    }

    private SyncTaskEntity get(long id) {
        // the engine and the log service ask only for tasks they have seen; tasks are never deleted
        return tasks.findById(id).orElseThrow(() -> new IllegalStateException("no sync task " + id));
    }

    private void write(SyncTaskEntity task, Instant at, String message) {
        log.save(new SyncLogEntity(task.getId(), at, message));
    }
}
