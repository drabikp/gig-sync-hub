package sk.drabikp.gigsynchub.sync.adapter.out.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import sk.drabikp.gigsynchub.gig.domain.Gig;
import sk.drabikp.gigsynchub.gig.domain.TestGigs;
import sk.drabikp.gigsynchub.gig.domain.platform.Platform;
import sk.drabikp.gigsynchub.sync.domain.SyncAction;
import sk.drabikp.gigsynchub.sync.domain.SyncLogEntry;
import sk.drabikp.gigsynchub.sync.domain.SyncStatus;
import sk.drabikp.gigsynchub.sync.domain.SyncTask;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static sk.drabikp.gigsynchub.gig.domain.TestBands.BAND;
import static sk.drabikp.gigsynchub.gig.domain.platform.TestPlatforms.BANDSINTOWN;
import static sk.drabikp.gigsynchub.gig.domain.platform.TestPlatforms.BANDZONE;

@SpringBootTest
@Transactional
class JpaSyncOutboxTest {

    private static final Instant NOW = Instant.parse("2026-10-08T10:00:00Z");

    @Autowired
    private JpaSyncOutbox outbox;

    private final Gig gig = TestGigs.gig("Fest", "Klub 007");
    private final Gig other = TestGigs.gig("Other", "Barrák");

    private SyncTask queue(Gig g, Platform platform, SyncAction action) {
        return outbox.enqueue(BAND, g.id(), SyncTask.labelOf(g), platform, action, NOW);
    }

    @Test
    void a_queued_task_is_pending_due_now_and_logged() {
        SyncTask task = queue(gig, BANDZONE, SyncAction.UPDATE);

        assertThat(task.status()).isEqualTo(SyncStatus.PENDING);
        assertThat(task.gigId()).isEqualTo(gig.id());
        assertThat(outbox.due(NOW)).containsExactly(task);
        assertThat(outbox.openFor(BAND, gig.id())).containsExactly(task);
        assertThat(outbox.log(task.band(), task.id())).extracting(SyncLogEntry::message).containsExactly("queued");
    }

    @Test
    void every_due_task_is_listed_oldest_first() {
        SyncTask onBit = queue(gig, BANDSINTOWN, SyncAction.UPDATE);
        SyncTask onBz = queue(other, BANDZONE, SyncAction.UPDATE);

        assertThat(outbox.due(NOW)).containsExactly(onBit, onBz);
    }

    @Test
    void a_task_runs_once_and_every_step_is_logged() {
        SyncTask task = queue(gig, BANDZONE, SyncAction.DELETE);

        assertThat(outbox.markRunning(task.band(), task.id(), NOW)).isTrue();
        assertThat(outbox.markRunning(task.band(), task.id(), NOW)).as("already running").isFalse();
        outbox.markRetry(task.band(), task.id(), "timeout", NOW.plus(Duration.ofMinutes(1)), NOW);
        assertThat(outbox.due(NOW)).as("not due before the retry time").isEmpty();
        assertThat(outbox.due(NOW.plus(Duration.ofMinutes(1)))).isNotEmpty();
        outbox.markRunning(task.band(), task.id(), NOW.plus(Duration.ofMinutes(1)));
        outbox.markDone(task.band(), task.id(), null, NOW.plus(Duration.ofMinutes(2)));

        SyncTask done = outbox.find(task.band(), task.id()).orElseThrow();
        assertThat(done.status()).isEqualTo(SyncStatus.DONE);
        assertThat(done.attempts()).isEqualTo(2);
        assertThat(outbox.log(task.band(), task.id())).extracting(SyncLogEntry::message).containsExactly(
                "queued", "attempt 1 started", "failed: timeout — will retry at 2026-10-08T10:01:00Z",
                "attempt 2 started", "done");
    }

    @Test
    void a_discarded_task_cannot_be_started_and_a_retry_starts_it_afresh() {
        SyncTask task = queue(gig, BANDZONE, SyncAction.PUBLISH);
        outbox.markRunning(task.band(), task.id(), NOW);
        outbox.markFailed(task.band(), task.id(), "wizard broke", NOW);
        assertThat(outbox.unfinished(BAND)).extracting(SyncTask::id).contains(task.id());

        outbox.discard(task.band(), task.id(), NOW);
        assertThat(outbox.markRunning(task.band(), task.id(), NOW)).isFalse();
        outbox.requeue(task.band(), task.id(), NOW);

        SyncTask again = outbox.find(task.band(), task.id()).orElseThrow();
        assertThat(again.status()).isEqualTo(SyncStatus.PENDING);
        assertThat(again.attempts()).isZero();
    }

    @Test
    void a_finished_task_stays_finished_whoever_writes_it_late() {
        SyncTask task = queue(gig, BANDZONE, SyncAction.UPDATE);
        outbox.markRunning(task.band(), task.id(), NOW);
        outbox.markDone(task.band(), task.id(), "updated", NOW);

        outbox.markFailed(task.band(), task.id(), "a late failure", NOW);
        outbox.markRetry(task.band(), task.id(), "a late retry", NOW, NOW);
        outbox.discard(task.band(), task.id(), NOW);
        outbox.requeue(task.band(), task.id(), NOW);

        assertThat(outbox.find(task.band(), task.id()).orElseThrow().status()).isEqualTo(SyncStatus.DONE);
        assertThat(outbox.log(task.band(), task.id())).extracting(SyncLogEntry::message)
                .containsExactly("queued", "attempt 1 started", "done — updated");
    }

    @Test
    void due_publishes_of_a_platform_come_together_oldest_first() {
        SyncTask a = queue(gig, BANDSINTOWN, SyncAction.PUBLISH);
        queue(gig, BANDZONE, SyncAction.PUBLISH);
        SyncTask b = queue(other, BANDSINTOWN, SyncAction.PUBLISH);

        assertThat(outbox.due(BAND, BANDSINTOWN, SyncAction.PUBLISH, NOW)).extracting(SyncTask::id)
                .containsExactly(a.id(), b.id());
    }

    @Test
    void superseding_discards_only_the_pending_tasks_of_that_gig_on_that_platform() {
        SyncTask pending = queue(gig, BANDZONE, SyncAction.UPDATE);
        SyncTask running = queue(gig, BANDZONE, SyncAction.CANCEL);
        outbox.markRunning(running.band(), running.id(), NOW);
        SyncTask elsewhere = queue(gig, BANDSINTOWN, SyncAction.UPDATE);

        assertThat(outbox.supersede(BAND, gig.id(), BANDZONE, "the gig was deleted", NOW)).extracting(SyncTask::id)
                .containsExactly(pending.id());
        assertThat(outbox.find(pending.band(), pending.id()).orElseThrow().status()).isEqualTo(SyncStatus.DISCARDED);
        assertThat(outbox.find(running.band(), running.id()).orElseThrow().status()).isEqualTo(SyncStatus.RUNNING);
        assertThat(outbox.find(elsewhere.band(), elsewhere.id()).orElseThrow().status()).isEqualTo(SyncStatus.PENDING);
    }

    @Test
    void recent_lists_the_newest_first() {
        SyncTask first = queue(gig, BANDZONE, SyncAction.UPDATE);
        SyncTask second = queue(other, BANDZONE, SyncAction.UPDATE);

        assertThat(outbox.recent(BAND, 1)).extracting(SyncTask::id).containsExactly(second.id());
        assertThat(outbox.recent(BAND, 10)).extracting(SyncTask::id).containsSubsequence(second.id(), first.id());
    }
}
