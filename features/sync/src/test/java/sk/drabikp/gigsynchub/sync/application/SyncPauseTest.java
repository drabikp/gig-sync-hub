package sk.drabikp.gigsynchub.sync.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static sk.drabikp.gigsynchub.gig.domain.TestBands.BAND;
import static sk.drabikp.gigsynchub.gig.domain.TestBands.OTHER;

class SyncPauseTest {

    private final SyncFakes.Signals signals = new SyncFakes.Signals();
    private final SyncFakes.Settings settings = new SyncFakes.Settings();

    @Test
    void a_pause_survives_a_restart_until_the_user_resumes() {
        new SyncPause(signals, signals, settings).pause(BAND);

        SyncPause afterRestart = new SyncPause(signals, signals, settings);
        assertThat(afterRestart.paused(BAND)).isTrue();

        afterRestart.resume(BAND);
        assertThat(new SyncPause(signals, signals, settings).paused(BAND)).isFalse();
        assertThat(signals.wakes).as("resuming wakes the worker").isEqualTo(1);
    }

    @Test
    void one_bands_pause_is_its_own() {
        SyncPause pause = new SyncPause(signals, signals, settings);

        pause.pause(BAND);

        assertThat(pause.paused(OTHER)).isFalse();
        assertThat(pause.pausedBands()).containsExactly(BAND);
    }
}
