package sk.drabikp.bzscraper.sync.domain;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.gig.domain.TestGigs;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static sk.drabikp.bzscraper.gig.domain.TestBands.BAND;
import static sk.drabikp.bzscraper.gig.domain.platform.TestPlatforms.BANDSINTOWN;
import static sk.drabikp.bzscraper.gig.domain.platform.TestPlatforms.BANDZONE;

/** What a gig's unfinished work says about the work a change to it still needs. */
class OpenWorkTest {

    private static final GigId GIG = TestGigs.id("2026-10-17|klub");
    private static final Instant NOW = Instant.parse("2026-10-09T10:00:00Z");

    private static SyncTask task(Platform platform, SyncAction action, SyncStatus status) {
        return new SyncTask(1, BAND, GIG, "gig", platform, action, status, 0, NOW, NOW, NOW, null, null);
    }

    private static OpenWork work(SyncTask... tasks) {
        return OpenWork.of(List.of(tasks));
    }

    @Test
    void work_is_open_whether_it_waits_or_runs_but_only_waiting_work_is_unseen_by_the_platform() {
        OpenWork running = work(task(BANDZONE, SyncAction.PUBLISH, SyncStatus.RUNNING));

        assertThat(running.has(BANDZONE, SyncAction.PUBLISH)).isTrue();
        assertThat(running.pending(BANDZONE, SyncAction.PUBLISH)).as("the platform is being told right now").isFalse();
    }

    @Test
    void an_update_is_already_coming_when_a_publish_or_an_update_waits_there() {
        assertThat(work(task(BANDZONE, SyncAction.PUBLISH, SyncStatus.PENDING)).updateAlreadyComing(BANDZONE)).isTrue();
        assertThat(work(task(BANDZONE, SyncAction.UPDATE, SyncStatus.PENDING)).updateAlreadyComing(BANDZONE)).isTrue();
        assertThat(work(task(BANDZONE, SyncAction.CANCEL, SyncStatus.PENDING)).updateAlreadyComing(BANDZONE))
                .as("a cancel carries no details").isFalse();
        assertThat(work(task(BANDZONE, SyncAction.UPDATE, SyncStatus.RUNNING)).updateAlreadyComing(BANDZONE))
                .as("already on its way with what the gig said then").isFalse();
    }

    @Test
    void a_cancel_and_a_reactivate_undo_each_other_while_the_platform_has_seen_neither() {
        OpenWork reactivating = work(task(BANDZONE, SyncAction.REACTIVATE, SyncStatus.PENDING));

        assertThat(reactivating.undoesWaitingWork(BANDZONE, SyncAction.CANCEL)).isTrue();
        assertThat(work(task(BANDZONE, SyncAction.CANCEL, SyncStatus.PENDING))
                .undoesWaitingWork(BANDZONE, SyncAction.REACTIVATE)).isTrue();
        assertThat(reactivating.undoesWaitingWork(BANDZONE, SyncAction.UPDATE))
                .as("nothing undoes an update").isFalse();
        assertThat(work(task(BANDZONE, SyncAction.REACTIVATE, SyncStatus.RUNNING))
                .undoesWaitingWork(BANDZONE, SyncAction.CANCEL)).as("the platform is hearing it now").isFalse();
    }

    @Test
    void one_platforms_work_says_nothing_about_another() {
        OpenWork onBandzone = work(task(BANDZONE, SyncAction.UPDATE, SyncStatus.PENDING));

        assertThat(onBandzone.updateAlreadyComing(BANDSINTOWN)).isFalse();
        assertThat(onBandzone.has(BANDSINTOWN, SyncAction.UPDATE)).isFalse();
        assertThat(OpenWork.of(List.of()).updateAlreadyComing(BANDZONE)).isFalse();
    }

    @Test
    void a_new_task_takes_over_the_failed_work_it_does_again_but_never_a_failed_publish() {
        assertThat(SyncAction.UPDATE.replaces()).containsExactly(SyncAction.UPDATE);
        assertThat(SyncAction.CANCEL.replaces()).containsExactlyInAnyOrder(SyncAction.CANCEL, SyncAction.UPDATE);
        assertThat(SyncAction.DELETE.replaces()).isEqualTo(SyncAction.repeatables())
                .doesNotContain(SyncAction.PUBLISH, SyncAction.REACTIVATE);
        assertThat(SyncAction.repeatables()).allMatch(SyncAction::repeatable);
        assertThat(SyncAction.PUBLISH.opposite()).isEmpty();
        assertThat(SyncAction.CANCEL.opposite()).contains(SyncAction.REACTIVATE);
    }
}
