package sk.drabikp.gigsynchub.sync.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SyncRetryPolicyTest {

    private static final Instant NOW = Instant.parse("2026-10-08T10:00:00Z");

    @Test
    void repeatable_work_is_retried_after_1_5_and_15_minutes_then_given_up() {
        assertThat(SyncRetryPolicy.nextAttempt(SyncAction.DELETE, 1, NOW)).contains(NOW.plus(Duration.ofMinutes(1)));
        assertThat(SyncRetryPolicy.nextAttempt(SyncAction.UPDATE, 2, NOW)).contains(NOW.plus(Duration.ofMinutes(5)));
        assertThat(SyncRetryPolicy.nextAttempt(SyncAction.CANCEL, 3, NOW)).contains(NOW.plus(Duration.ofMinutes(15)));
        assertThat(SyncRetryPolicy.nextAttempt(SyncAction.CANCEL, 4, NOW)).isEmpty();
        assertThat(SyncRetryPolicy.maxAttempts()).isEqualTo(4);
    }

    @Test
    void publishing_and_reactivating_are_never_retried_automatically() {
        assertThat(SyncRetryPolicy.nextAttempt(SyncAction.PUBLISH, 1, NOW)).isEmpty();
        assertThat(SyncRetryPolicy.nextAttempt(SyncAction.REACTIVATE, 1, NOW)).isEmpty();
    }
}
