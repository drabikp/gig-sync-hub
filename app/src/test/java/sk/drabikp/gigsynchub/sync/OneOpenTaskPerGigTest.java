package sk.drabikp.gigsynchub.sync;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import sk.drabikp.gigsynchub.gig.application.port.out.Transactions;
import sk.drabikp.gigsynchub.gig.domain.GigId;
import sk.drabikp.gigsynchub.gig.domain.platform.Platform;
import sk.drabikp.gigsynchub.gig.domain.platform.Platforms;
import sk.drabikp.gigsynchub.sync.application.port.out.SyncOutbox;
import sk.drabikp.gigsynchub.sync.domain.SyncAction;
import sk.drabikp.gigsynchub.sync.domain.SyncStatus;
import sk.drabikp.gigsynchub.sync.domain.SyncTask;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static sk.drabikp.gigsynchub.gig.domain.TestBands.BAND;

/**
 * One piece of WAITING work per gig, platform and action — the first of the outbox's queueing rules
 * the database itself holds ({@code sync_task_open_work}, V13).
 *
 * <p>Those rules are read-then-write: {@code SyncRequests} asks what is open for a gig and then
 * queues whatever is missing, so two requests for one gig can both find nothing and both queue.
 * The engine would then put both rows in one workload with every intake computed before the first
 * batch ran, and the platform would be told twice — on Bandsintown, two live events of which only
 * the second is ever recorded. What makes the second request harmless is this index, and these are
 * the tests that say so.
 *
 * <p>Not {@code @Transactional} — it is about separate, real transactions.
 */
@SpringBootTest
class OneOpenTaskPerGigTest {

    private static final Instant NOW = Instant.parse("2026-10-13T10:00:00Z");

    @Autowired
    private SyncOutbox outbox;
    @Autowired
    private Transactions transactions;
    @Autowired
    private Platforms platforms;

    private final GigId gig = GigId.fresh();
    /** Written from both threads of the race below. */
    private final List<Long> made = Collections.synchronizedList(new ArrayList<>());

    /**
     * Done, not discarded: a RUNNING task may not become DISCARDED ({@code SyncStatus#canBecome}),
     * and leaving one RUNNING behind would make band #1 look busy to every test after this one.
     */
    @AfterEach
    void settleWhatWasQueued() {
        transactions.inTransaction(() -> made.forEach(id -> outbox.markDone(id, "the test is over", NOW)));
    }

    private Platform somewhere() {
        return platforms.all().getFirst();
    }

    private SyncTask queue(SyncAction action) {
        SyncTask task = transactions.computeInTransaction(
                () -> outbox.enqueue(BAND, gig, "A gig", somewhere(), action, NOW));
        made.add(task.id());
        return task;
    }

    @Test
    void the_same_work_cannot_wait_twice_for_one_gig_on_one_platform() {
        queue(SyncAction.UPDATE);

        assertThatThrownBy(() -> queue(SyncAction.UPDATE))
                .as("the database refuses the second one, whoever asked for it")
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(outbox.openFor(BAND, gig)).singleElement()
                .satisfies(task -> assertThat(task.status()).isEqualTo(SyncStatus.PENDING));
    }

    @Test
    void work_of_another_action_may_wait_at_the_same_time() {
        queue(SyncAction.UPDATE);

        assertThatCode(() -> queue(SyncAction.CANCEL))
                .as("the index is per action: a cancel is not the update's twin")
                .doesNotThrowAnyException();
        assertThat(outbox.openFor(BAND, gig)).hasSize(2);
    }

    /**
     * The index is on PENDING alone, deliberately: a running update may already have sent the
     * details the user has only just changed, which is why {@code OpenWork#updateAlreadyComing}
     * asks about waiting work and not about running work. Were RUNNING in the index, this edit
     * would be refused — so this test is what keeps the index from being widened.
     */
    @Test
    void an_update_may_wait_behind_one_that_is_already_running() {
        SyncTask running = queue(SyncAction.UPDATE);
        transactions.inTransaction(() -> assertThat(outbox.markRunning(running.id(), NOW)).isTrue());

        assertThatCode(() -> queue(SyncAction.UPDATE))
                .as("an edit while the update runs queues a fresh one behind it")
                .doesNotThrowAnyException();

        assertThat(outbox.openFor(BAND, gig)).hasSize(2)
                .extracting(SyncTask::status)
                .containsExactlyInAnyOrder(SyncStatus.RUNNING, SyncStatus.PENDING);
    }

    @Test
    void two_requests_for_one_gig_at_the_same_moment_queue_it_once() throws Exception {
        CyclicBarrier together = new CyclicBarrier(2);
        List<Throwable> refused = new ArrayList<>();

        List<Thread> both = List.of(queues(together, refused), queues(together, refused));
        both.forEach(Thread::start);
        for (Thread thread : both) {
            thread.join(TimeUnit.SECONDS.toMillis(20));
        }

        assertThat(outbox.openFor(BAND, gig))
                .as("one piece of work waits, whichever of them won")
                .singleElement()
                .satisfies(task -> assertThat(task.action()).isEqualTo(SyncAction.PUBLISH));
        assertThat(refused).as("and the loser was told, rather than queueing a second one").hasSize(1);
    }

    private Thread queues(CyclicBarrier together, List<Throwable> refused) {
        return new Thread(() -> {
            try {
                together.await(20, TimeUnit.SECONDS);
                queue(SyncAction.PUBLISH);
            } catch (Throwable e) {
                synchronized (refused) {
                    refused.add(e);
                }
            }
        }, "queueing-" + gig.value());
    }
}
