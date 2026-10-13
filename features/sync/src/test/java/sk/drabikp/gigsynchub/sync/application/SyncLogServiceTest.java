package sk.drabikp.gigsynchub.sync.application;

import org.junit.jupiter.api.Test;
import sk.drabikp.gigsynchub.gig.application.NotFoundException;
import sk.drabikp.gigsynchub.gig.application.UserFacingException;
import sk.drabikp.gigsynchub.gig.domain.Gig;
import sk.drabikp.gigsynchub.gig.domain.TestGigs;
import sk.drabikp.gigsynchub.sync.application.port.in.SyncErrorCode;
import sk.drabikp.gigsynchub.sync.application.port.in.SyncLogUseCase.Show;
import sk.drabikp.gigsynchub.sync.domain.SyncAction;
import sk.drabikp.gigsynchub.sync.domain.SyncCounts;
import sk.drabikp.gigsynchub.sync.domain.SyncStatus;
import sk.drabikp.gigsynchub.sync.domain.SyncTask;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static sk.drabikp.gigsynchub.gig.domain.TestBands.BAND;
import static sk.drabikp.gigsynchub.gig.domain.TestBands.OTHER;
import static sk.drabikp.gigsynchub.gig.domain.platform.TestPlatforms.BANDSINTOWN;

class SyncLogServiceTest {

    private final SyncFakes.Outbox outbox = new SyncFakes.Outbox();
    private final SyncFakes.Signals signals = new SyncFakes.Signals();
    private final SyncFakes.MutableClock clock = new SyncFakes.MutableClock();
    private final SyncLogService service = new SyncLogService(outbox, signals, signals, clock);
    private final Gig gig = TestGigs.gig("Fest", "Klub 007");

    private SyncTask task() {
        return outbox.enqueue(BAND, gig.id(), SyncTask.labelOf(gig), BANDSINTOWN, SyncAction.PUBLISH, clock.instant());
    }

    @Test
    void a_failed_task_is_retried_from_scratch_and_wakes_the_worker() {
        SyncTask task = task();
        outbox.markRunning(task.band(), task.id(), clock.instant());
        outbox.markFailed(task.band(), task.id(), "wizard broke", clock.instant());

        service.retry(BAND, task.id());

        assertThat(outbox.get(task.id()).status()).isEqualTo(SyncStatus.PENDING);
        assertThat(outbox.get(task.id()).attempts()).isZero();
        assertThat(signals.wakes).isEqualTo(1);
    }

    @Test
    void only_failed_or_discarded_tasks_can_be_retried_and_only_pending_or_failed_discarded() {
        SyncTask pending = task();
        SyncTask running = task();
        outbox.markRunning(running.band(), running.id(), clock.instant());

        assertThatThrownBy(() -> service.retry(BAND, pending.id())).isInstanceOf(UserFacingException.class);
        assertThatThrownBy(() -> service.discard(BAND, running.id())).isInstanceOf(UserFacingException.class);

        service.discard(BAND, pending.id());
        assertThat(outbox.get(pending.id()).status()).isEqualTo(SyncStatus.DISCARDED);
        service.retry(BAND, pending.id());
        assertThat(outbox.get(pending.id()).status()).isEqualTo(SyncStatus.PENDING);
    }

    @Test
    void the_log_of_a_task_lists_what_happened_to_it() {
        SyncTask task = task();
        outbox.markRunning(task.band(), task.id(), clock.instant());
        outbox.markDone(task.band(), task.id(), null, clock.instant());

        assertThat(service.log(BAND, task.id())).hasSize(3);
        assertThat(service.unfinished(BAND)).isEmpty();
        assertThat(service.history(BAND, gig.id(), 10)).extracting(SyncTask::id).containsExactly(task.id());
    }

    @Test
    void another_bands_task_is_not_there_for_the_band() {
        SyncTask theirs = outbox.enqueue(OTHER, gig.id(), SyncTask.labelOf(gig), BANDSINTOWN, SyncAction.PUBLISH,
                clock.instant());

        assertThatThrownBy(() -> service.log(BAND, theirs.id())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.discard(BAND, theirs.id())).isInstanceOf(NotFoundException.class);
        assertThat(service.tasks(BAND, Show.ALL)).isEmpty();
        assertThat(outbox.get(theirs.id()).status()).isEqualTo(SyncStatus.PENDING);
    }

    @Test
    void a_task_that_isnt_there_is_not_found_and_one_that_moved_on_is_refused_with_its_code() {
        SyncTask running = task();
        outbox.markRunning(running.band(), running.id(), clock.instant());

        assertThatThrownBy(() -> service.retry(BAND, 999)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.retry(BAND, running.id())).isInstanceOfSatisfying(UserFacingException.class, e -> {
            assertThat(e.code()).isEqualTo(SyncErrorCode.TASK_MOVED_ON);
            assertThat(e.args()).containsEntry("status", "RUNNING").containsEntry("gig", SyncTask.labelOf(gig));
        });
    }

    /**
     * The page shows each row's {@code updatedAt} and groups the rows by its day, so every list is
     * ordered by that — not by the order the work was queued in, which is the queue's business. A
     * retry is the plain case: the oldest task becomes the newest activity.
     */
    @Test
    void every_list_puts_what_happened_last_first_even_if_it_was_queued_first() {
        SyncTask first = task();
        SyncTask second = task();
        outbox.markRunning(second.band(), second.id(), clock.instant());
        outbox.markFailed(second.band(), second.id(), "refused", clock.instant());
        clock.advance(Duration.ofHours(2));
        outbox.markRunning(first.band(), first.id(), clock.instant());
        outbox.markFailed(first.band(), first.id(), "refused later", clock.instant());

        assertThat(service.tasks(BAND, Show.ALL)).extracting(SyncTask::id).containsExactly(first.id(), second.id());
        assertThat(service.tasks(BAND, Show.OPEN)).extracting(SyncTask::id).containsExactly(first.id(), second.id());
        assertThat(service.tasks(BAND, Show.FAILED)).extracting(SyncTask::id).containsExactly(first.id(), second.id());
    }

    @Test
    void the_lists_show_the_latest_the_open_or_the_failed_tasks_newest_first_and_count_the_open_ones() {
        SyncTask queued = task();
        SyncTask retrying = task();
        outbox.markRunning(retrying.band(), retrying.id(), clock.instant());
        outbox.markRetry(retrying.band(), retrying.id(), "timeout", clock.instant().plusSeconds(60), clock.instant());
        SyncTask running = task();
        outbox.markRunning(running.band(), running.id(), clock.instant());
        SyncTask failed = task();
        outbox.markRunning(failed.band(), failed.id(), clock.instant());
        outbox.markFailed(failed.band(), failed.id(), "refused", clock.instant());
        SyncTask done = task();
        outbox.markRunning(done.band(), done.id(), clock.instant());
        outbox.markDone(done.band(), done.id(), null, clock.instant());

        assertThat(service.tasks(BAND, Show.ALL)).extracting(SyncTask::id)
                .containsExactly(done.id(), failed.id(), running.id(), retrying.id(), queued.id());
        assertThat(service.tasks(BAND, Show.OPEN)).extracting(SyncTask::id)
                .containsExactly(failed.id(), running.id(), retrying.id(), queued.id());
        assertThat(service.tasks(BAND, Show.FAILED)).extracting(SyncTask::id).containsExactly(failed.id());
        assertThat(service.counts(BAND)).isEqualTo(new SyncCounts(1, 1, 1, 1));
    }
}
