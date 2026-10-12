package sk.drabikp.gigsynchub.check.application;

import org.junit.jupiter.api.Test;
import sk.drabikp.gigsynchub.check.application.port.in.CheckPlatformsUseCase;
import sk.drabikp.gigsynchub.check.domain.PlatformCheck;
import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.gig.domain.GigId;
import sk.drabikp.gigsynchub.gig.domain.platform.Platform;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static sk.drabikp.gigsynchub.gig.domain.TestBands.BAND;

class BackgroundCheckTest {

    private final Check check = new Check();
    private final List<Runnable> background = new ArrayList<>();
    private final BackgroundCheck start = new BackgroundCheck(check, background::add);

    @Test
    void a_check_the_user_starts_runs_in_the_background() {
        start.start(BAND);

        assertThat(check.checks).isZero();
        background.getFirst().run();
        assertThat(check.checks).isEqualTo(1);
    }

    @Test
    void nothing_starts_while_a_check_runs_and_a_failed_one_doesnt_escape() {
        check.running = true;
        start.start(BAND);
        assertThat(background).isEmpty();

        check.running = false;
        check.fails = true;
        start.start(BAND);
        background.getFirst().run();                          // logged, not thrown into the executor
    }

    private static final class Check implements CheckPlatformsUseCase {

        private boolean running;
        private boolean fails;
        private int checks;

        @Override
        public Optional<PlatformCheck> check(BandId band) {
            if (fails) {
                throw new IllegalStateException("a platform's page changed");
            }
            checks++;
            return Optional.empty();
        }

        @Override
        public Optional<PlatformCheck> lastCheck(BandId band) {
            return Optional.empty();
        }

        @Override
        public boolean running(BandId band) {
            return running;
        }

        @Override
        public void forget(BandId band, Platform platform, GigId gigId) {
        }

        @Override
        public void dismiss(BandId band, Platform platform, GigId gigId) {
        }
    }
}
