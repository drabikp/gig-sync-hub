package sk.drabikp.gigsynchub.sync.domain;

import org.junit.jupiter.api.Test;
import sk.drabikp.gigsynchub.gig.domain.TestGigs;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static sk.drabikp.gigsynchub.gig.domain.TestBands.BAND;
import static sk.drabikp.gigsynchub.gig.domain.platform.TestPlatforms.BANDZONE;

class PlatformStateTest {

    private static SyncTask task(SyncStatus status, int attempts) {
        Instant now = Instant.parse("2026-10-09T10:00:00Z");
        return new SyncTask(1, BAND, TestGigs.id("2026-10-17|klub"), "gig", BANDZONE, SyncAction.UPDATE,
                status, attempts, now, now, now, null, null);
    }

    @Test
    void without_work_it_is_live_where_published_and_else_not_there() {
        assertThat(PlatformState.of(true, null)).isEqualTo(PlatformState.LIVE);
        assertThat(PlatformState.of(false, null)).isEqualTo(PlatformState.NONE);
    }

    @Test
    void the_latest_unfinished_task_decides() {
        assertThat(PlatformState.of(true, task(SyncStatus.PENDING, 0))).isEqualTo(PlatformState.QUEUED);
        assertThat(PlatformState.of(true, task(SyncStatus.PENDING, 1))).isEqualTo(PlatformState.RETRYING);
        assertThat(PlatformState.of(false, task(SyncStatus.RUNNING, 1))).isEqualTo(PlatformState.RUNNING);
        assertThat(PlatformState.of(true, task(SyncStatus.FAILED, 3))).isEqualTo(PlatformState.FAILED);
    }
}
