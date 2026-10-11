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
        assertThat(outbox().due(NOW)).extracting(SyncTask::band).containsExactly(OTHER);
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

        outbox().markRunning(first.id(), NOW);
        outbox().markDone(first.id(), "published", NOW.plus(Duration.ofMinutes(5)));

        assertThat(outbox().history(BAND, gig.id(), 10)).extracting(SyncTask::id)
                .as("the gig's page shows each entry's time, so the one that moved last comes first")
                .containsExactly(first.id(), second.id());
    }

    /**
     * The Activity page shows each row's {@code updatedAt} and groups the rows by its day, so the list
     * must be ordered by it — by id, a task retried today sits wherever it was first queued and the
     * times on the page read backwards.
     */
    @Test
    void the_recent_list_is_a_log_of_activity_whatever_order_the_work_was_asked_in() {
        SyncTask asked_first = queue(gig, SyncAction.PUBLISH);
        SyncTask asked_second = queue(other, SyncAction.PUBLISH);
        outbox().markRunning(asked_second.id(), NOW);
        outbox().markDone(asked_second.id(), "published", NOW.plus(Duration.ofMinutes(1)));
        outbox().markRunning(asked_first.id(), NOW);
        outbox().markDone(asked_first.id(), "published", NOW.plus(Duration.ofMinutes(5)));

        assertThat(outbox().recent(BAND, 10)).extracting(SyncTask::id)
                .as("what happened last comes first")
                .containsExactly(asked_first.id(), asked_second.id());
        assertThat(outbox().recent(BAND, 1)).extracting(SyncTask::id)
                .as("and a shorter list keeps the latest, not the oldest")
                .containsExactly(asked_first.id());
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

    /**
     * An edit can move a gig to another day or rename it. Which gig the work is about does not
     * change with it — that is what a gig's own identity is for — but what the work is CALLED does,
     * for the work that has not happened yet. What is settled keeps the label it was done with: the
     * log says what the work was about when it ran.
     */
    @Test
    void the_waiting_work_says_how_the_gig_reads_now_and_the_log_keeps_what_it_said() {
        SyncTask waiting = queue(gig, SyncAction.UPDATE);
        SyncTask settled = queue(gig, SyncAction.CANCEL);
        outbox().markRunning(settled.id(), NOW);
        outbox().markDone(settled.id(), "cancelled there", NOW);
        String asItWas = get(settled).gigLabel();

        outbox().relabel(BAND, gig.id(), "2026-12-24 · Fest, Brno");

        assertThat(get(waiting).gigId()).as("which gig it is cannot change").isEqualTo(gig.id());
        assertThat(get(waiting).gigLabel()).isEqualTo("2026-12-24 · Fest, Brno");
        assertThat(get(settled).gigLabel()).as("already done: the log is not rewritten").isEqualTo(asItWas);
        assertThat(outbox().openFor(BAND, gig.id())).extracting(SyncTask::id).containsExactly(waiting.id());
    }
}
