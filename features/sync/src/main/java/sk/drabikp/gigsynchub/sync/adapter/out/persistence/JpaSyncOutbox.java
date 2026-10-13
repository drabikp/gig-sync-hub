package sk.drabikp.gigsynchub.sync.adapter.out.persistence;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.gig.domain.GigId;
import sk.drabikp.gigsynchub.gig.domain.platform.Platform;
import sk.drabikp.gigsynchub.sync.application.port.out.SyncOutbox;
import sk.drabikp.gigsynchub.sync.domain.StepType;
import sk.drabikp.gigsynchub.sync.domain.SyncAction;
import sk.drabikp.gigsynchub.sync.domain.SyncLogEntry;
import sk.drabikp.gigsynchub.sync.domain.SyncStatus;
import sk.drabikp.gigsynchub.sync.domain.SyncTask;

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
        SyncTaskEntity task = tasks.save(new SyncTaskEntity(band.value(), gigId.value(), gigLabel, platform,
                action, now));
        write(task, now, "queued");
        return mapper.toTask(task);
    }

    @Override
    public Optional<SyncTask> find(BandId band, long id) {
        return tasks.findById(id).filter(task -> task.getBandId().equals(band.value())).map(mapper::toTask);
    }

    @Override
    public List<SyncTask> openFor(BandId band, GigId gigId) {
        return mapper.toTasks(tasks.findByBandIdAndGigIdAndStatusInOrderByIdAsc(band.value(), gigId.value(), OPEN));
    }

    @Override
    public List<SyncTask> unfinished(BandId band) {
        return mapper.toTasks(tasks.findByBandIdAndStatusInOrderByIdAsc(band.value(), UNFINISHED));
    }

    @Override
    public List<SyncTask> recent(BandId band, int limit) {
        return mapper.toTasks(tasks.findByBandIdOrderByUpdatedAtDescIdDesc(band.value(), Limit.of(limit)));
    }

    @Override
    public List<SyncTask> history(BandId band, GigId gigId, int limit) {
        return mapper.toTasks(tasks.findByBandIdAndGigIdOrderByUpdatedAtDescIdDesc(band.value(), gigId.value(), Limit.of(limit)));
    }

    @Override
    public List<SyncLogEntry> log(long taskId) {
        return mapper.toEntries(log.findByTaskIdOrderByIdAsc(taskId));
    }

    @Override
    @Transactional
    public List<SyncTask> supersede(BandId band, GigId gigId, Platform platform, String why, Instant now) {
        List<SyncTaskEntity> pending = tasks.findByBandIdAndGigIdAndPlatformAndStatusOrderByIdAsc(
                band.value(), gigId.value(), platform.id(), SyncStatus.PENDING.name());
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
                        band.value(), gigId.value(), platform.id(), SyncStatus.FAILED.name()).stream()
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
    public void relabel(BandId band, GigId gigId, String gigLabel) {
        for (SyncTaskEntity task : tasks.findByBandIdAndGigIdAndStatusInOrderByIdAsc(band.value(), gigId.value(),
                OPEN)) {
            task.relabel(gigLabel);
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
    public boolean markRunning(BandId band, long id, Instant now) {
        SyncTaskEntity task = get(band, id);
        if (task.status() != SyncStatus.PENDING) {
            return false;
        }
        task.startAttempt(now);
        write(task, now, "attempt " + task.getAttempts() + " started");
        return true;
    }

    @Override
    @Transactional
    public void markDone(BandId band, long id, String message, Instant now) {
        SyncTaskEntity task = get(band, id);
        if (task.change(SyncStatus.DONE, null, message, now)) {
            write(task, now, message == null || message.isBlank() ? "done" : "done — " + message);
        }
    }

    @Override
    @Transactional
    public void markRetry(BandId band, long id, String error, Instant nextAttempt, Instant now) {
        SyncTaskEntity task = get(band, id);
        if (task.change(SyncStatus.PENDING, nextAttempt, error, now)) {
            write(task, now, "failed: " + error + " — will retry at " + nextAttempt);
        }
    }

    @Override
    @Transactional
    public void markFailed(BandId band, long id, String error, Instant now) {
        SyncTaskEntity task = get(band, id);
        if (task.change(SyncStatus.FAILED, null, error, now)) {
            write(task, now, "failed: " + error + " — waiting for you to Retry or Discard");
        }
    }

    @Override
    @Transactional
    public void postpone(BandId band, long id, String why, Instant nextAttempt, Instant now) {
        SyncTaskEntity task = get(band, id);
        if (task.status() == SyncStatus.RUNNING && task.change(SyncStatus.PENDING, nextAttempt, why, now)) {
            task.undoAttempt();
            write(task, now, "postponed: " + why + " — again at " + nextAttempt);
        }
    }

    @Override
    @Transactional
    public void advance(BandId band, long id, StepType next, String why, Instant now) {
        SyncTaskEntity task = get(band, id);
        if (task.advance(next, now)) {
            write(task, now, why + " → " + next.label());
        }
    }

    @Override
    @Transactional
    public void requeue(BandId band, long id, Instant now) {
        SyncTaskEntity task = get(band, id);
        if (task.change(SyncStatus.PENDING, now, null, now)) {
            task.resetAttempts();
            write(task, now, "retried by you");
        }
    }

    @Override
    @Transactional
    public void discard(BandId band, long id, Instant now) {
        SyncTaskEntity task = get(band, id);
        if (task.change(SyncStatus.DISCARDED, null, "discarded by you", now)) {
            write(task, now, "discarded by you");
        }
    }

    /**
     * The band's task. The BAND IS PART OF THE ASKING — a task's id comes from a sequence every band
     * draws on, so an id by itself says nothing about whose work it is, and every method above takes
     * the band for that reason. Tasks are never deleted, so a miss here means the id was not this
     * band's, which can only be a bug in the caller: it is loud, and {@code SyncOutboxContract}
     * holds the in-memory outbox to the same.
     *
     * <p>Fetched by primary key and then checked, rather than asked for by band and id together.
     * That is not the obvious way round and it is deliberate: a derived query is JPQL, JPQL
     * auto-flushes, and {@code sync_log.task_id} is a plain column with no mapped association (this
     * schema has none, on purpose), so Hibernate cannot see that a log row depends on its task. With
     * {@code order_inserts} it batches the log rows first, and a flush in the middle of a
     * transaction that has queued tasks fails on the foreign key. A primary-key lookup is served
     * from the session without flushing, so the batching V9 exists for is left alone. The row never
     * leaves this method unchecked.
     */
    private SyncTaskEntity get(BandId band, long id) {
        SyncTaskEntity task = tasks.findById(id)
                .filter(found -> found.getBandId().equals(band.value()))
                .orElseThrow(() -> new IllegalStateException("no sync task " + id + " for this band"));
        return task;
    }

    private void write(SyncTaskEntity task, Instant at, String message) {
        log.save(new SyncLogEntity(task.getId(), at, message));
    }
}
