package sk.drabikp.bzscraper.sync.application.port.out;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.TestGigs;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.sync.domain.StepType;
import sk.drabikp.bzscraper.sync.domain.SyncAction;
import sk.drabikp.bzscraper.sync.domain.SyncStatus;
import sk.drabikp.bzscraper.sync.domain.SyncTask;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static sk.drabikp.bzscraper.gig.domain.TestBands.BAND;
import static sk.drabikp.bzscraper.gig.domain.TestBands.OTHER;
import static sk.drabikp.bzscraper.gig.domain.platform.TestPlatforms.BANDSINTOWN;
import static sk.drabikp.bzscraper.gig.domain.platform.TestPlatforms.BANDZONE;

/**
 * What every {@link SyncOutbox} does — the in-memory one the service tests use and the JPA one
 * the app uses — so the service tests' fake can't drift from the real outbox.
 */
public abstract class SyncOutboxContract {

    protected static final Instant NOW = Instant.parse("2026-10-08T10:00:00Z");

    private final Gig gig = TestGigs.gig("Fest", "Klub 007");
    private final Gig other = TestGigs.gig("Other", "Barrák");

    protected abstract SyncOutbox outbox();

    private SyncTask queue(Gig g, SyncAction action) {
        return outbox().enqueue(BAND, g.id(), SyncTask.labelOf(g), BANDZONE, action, NOW);
    }

    private SyncTask get(SyncTask task) {
        return outbox().find(task.id()).orElseThrow();
    }

    @Test
    void a_queued_task_is_pending_and_due_now() {
        SyncTask task = queue(gig, SyncAction.UPDATE);

        assertThat(task.status()).isEqualTo(SyncStatus.PENDING);
        assertThat(outbox().nextDue(NOW)).contains(task);
        assertThat(outbox().openFor(BAND, gig.id())).containsExactly(task);
        assertThat(outbox().due(BAND, BANDZONE, SyncAction.UPDATE, NOW)).containsExactly(task);
        assertThat(outbox().due(BAND, BANDSINTOWN, SyncAction.UPDATE, NOW)).isEmpty();
    }

    @Test
    void a_bands_tasks_are_its_own_even_for_the_same_gig() {
        SyncTask mine = queue(gig, SyncAction.UPDATE);
        SyncTask theirs = outbox().enqueue(OTHER, gig.id(), SyncTask.labelOf(gig), BANDZONE, SyncAction.UPDATE, NOW);

        assertThat(outbox().openFor(BAND, gig.id())).extracting(SyncTask::id).containsExactly(mine.id());
        assertThat(outbox().unfinished(OTHER)).extracting(SyncTask::id).containsExactly(theirs.id());
        assertThat(outbox().due(OTHER, BANDZONE, SyncAction.UPDATE, NOW)).extracting(SyncTask::id)
                .containsExactly(theirs.id());
        assertThat(outbox().supersede(BAND, gig.id(), BANDZONE, "test", NOW)).extracting(SyncTask::id)
                .containsExactly(mine.id());
        assertThat(get(theirs).status()).isEqualTo(SyncStatus.PENDING);
        assertThat(outbox().nextDue(NOW, Set.of(), Set.of(OTHER))).isEmpty();
    }

    @Test
    void a_platform_is_known_by_its_id_whichever_instance_asks() {
        SyncTask task = queue(gig, SyncAction.UPDATE);
        Platform sameId = Platform.of(BANDZONE.id());

        assertThat(get(task).platform()).isEqualTo(BANDZONE);
        assertThat(outbox().due(BAND, sameId, SyncAction.UPDATE, NOW)).extracting(SyncTask::id).containsExactly(task.id());
        assertThat(outbox().supersede(BAND, gig.id(), sameId, "test", NOW)).extracting(SyncTask::id).containsExactly(task.id());
    }

    @Test
    void a_gigs_history_is_its_own_tasks_newest_first() {
        SyncTask first = queue(gig, SyncAction.PUBLISH);
        queue(other, SyncAction.PUBLISH);
        SyncTask second = queue(gig, SyncAction.UPDATE);

        assertThat(outbox().history(BAND, gig.id(), 10)).extracting(SyncTask::id).containsExactly(second.id(), first.id());
        assertThat(outbox().history(BAND, gig.id(), 1)).extracting(SyncTask::id).containsExactly(second.id());
    }

    @Test
    void a_task_starts_once_each_start_is_an_attempt_and_a_retry_waits() {
        SyncTask task = queue(gig, SyncAction.DELETE);

        assertThat(outbox().markRunning(task.id(), NOW)).isTrue();
        assertThat(outbox().markRunning(task.id(), NOW)).isFalse();
        assertThat(outbox().running()).extracting(SyncTask::id).containsExactly(task.id());
        outbox().markRetry(task.id(), "timeout", NOW.plus(Duration.ofMinutes(1)), NOW);

        assertThat(get(task).status()).isEqualTo(SyncStatus.PENDING);
        assertThat(get(task).attempts()).isEqualTo(1);
        assertThat(outbox().nextDue(NOW)).isEmpty();
        assertThat(outbox().nextDue(NOW.plus(Duration.ofMinutes(1)))).isPresent();
    }

    @Test
    void a_postponed_task_did_not_use_an_attempt() {
        SyncTask task = queue(gig, SyncAction.UPDATE);
        outbox().markRunning(task.id(), NOW);

        outbox().postpone(task.id(), "browser busy", NOW.plus(Duration.ofMinutes(1)), NOW);

        assertThat(get(task).status()).isEqualTo(SyncStatus.PENDING);
        assertThat(get(task).attempts()).isZero();
        assertThat(get(task).nextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(1)));
    }

    @Test
    void a_finished_task_stays_finished() {
        SyncTask task = queue(gig, SyncAction.UPDATE);
        outbox().markRunning(task.id(), NOW);
        outbox().markDone(task.id(), null, NOW);

        outbox().markFailed(task.id(), "late", NOW);
        outbox().markRetry(task.id(), "late", NOW, NOW);
        outbox().postpone(task.id(), "late", NOW, NOW);
        outbox().advance(task.id(), StepType.FORM_EDIT, "late", NOW);
        outbox().discard(task.id(), NOW);
        outbox().requeue(task.id(), NOW);

        assertThat(get(task).status()).isEqualTo(SyncStatus.DONE);
        assertThat(get(task).step()).isNull();
    }

    @Test
    void the_user_retries_a_failed_or_discarded_task_afresh_and_discards_only_waiting_or_failed_ones() {
        SyncTask task = queue(gig, SyncAction.PUBLISH);
        outbox().markRunning(task.id(), NOW);
        outbox().discard(task.id(), NOW);
        assertThat(get(task).status()).as("not while it runs").isEqualTo(SyncStatus.RUNNING);

        outbox().markFailed(task.id(), "wizard broke", NOW);
        outbox().discard(task.id(), NOW);
        assertThat(get(task).status()).isEqualTo(SyncStatus.DISCARDED);
        assertThat(outbox().markRunning(task.id(), NOW)).isFalse();

        outbox().requeue(task.id(), NOW);
        assertThat(get(task).status()).isEqualTo(SyncStatus.PENDING);
        assertThat(get(task).attempts()).isZero();
    }

    @Test
    void a_run_moves_on_to_another_step() {
        SyncTask task = queue(gig, SyncAction.UPDATE);
        outbox().markRunning(task.id(), NOW);

        outbox().advance(task.id(), StepType.FORM_EDIT, "refused", NOW);

        assertThat(get(task).status()).isEqualTo(SyncStatus.PENDING);
        assertThat(get(task).step()).isEqualTo(StepType.FORM_EDIT);
        assertThat(outbox().nextDue(NOW)).contains(get(task));
    }

    @Test
    void superseding_and_replacing_touch_only_that_gigs_tasks_in_that_state() {
        SyncTask pending = queue(gig, SyncAction.UPDATE);
        SyncTask failed = queue(gig, SyncAction.CANCEL);
        outbox().markRunning(failed.id(), NOW);
        outbox().markFailed(failed.id(), "x", NOW);
        SyncTask othersPending = queue(other, SyncAction.UPDATE);

        assertThat(outbox().supersede(BAND, gig.id(), BANDZONE, "deleted", NOW)).extracting(SyncTask::id)
                .containsExactly(pending.id());
        assertThat(outbox().replaceFailed(BAND, gig.id(), BANDZONE, Set.of(SyncAction.CANCEL), "deleted", NOW))
                .extracting(SyncTask::id).containsExactly(failed.id());

        assertThat(get(pending).status()).isEqualTo(SyncStatus.DISCARDED);
        assertThat(get(failed).status()).isEqualTo(SyncStatus.DISCARDED);
        assertThat(get(othersPending).status()).isEqualTo(SyncStatus.PENDING);
    }

    @Test
    void tasks_follow_a_gig_whose_identity_changed() {
        SyncTask task = queue(gig, SyncAction.UPDATE);

        outbox().move(BAND, gig.id(), other.id(), "moved");

        assertThat(get(task).gigId()).isEqualTo(other.id());
        assertThat(outbox().openFor(BAND, gig.id())).isEmpty();
        assertThat(outbox().openFor(BAND, other.id())).hasSize(1);
    }
}
