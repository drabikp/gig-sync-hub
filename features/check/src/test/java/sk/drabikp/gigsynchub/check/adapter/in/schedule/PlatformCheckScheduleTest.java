package sk.drabikp.gigsynchub.check.adapter.in.schedule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sk.drabikp.gigsynchub.accounts.application.port.in.BandDirectory;
import sk.drabikp.gigsynchub.accounts.domain.Band;
import sk.drabikp.gigsynchub.check.application.port.in.CheckPlatformsUseCase;
import sk.drabikp.gigsynchub.check.config.CheckProperties;
import sk.drabikp.gigsynchub.check.domain.PlatformCheck;
import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.gig.domain.GigId;
import sk.drabikp.gigsynchub.gig.domain.platform.Platform;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A night is a budget, and the bands take turns within it.
 *
 * <p>What this is really about is the band at the end of the list. One band's check is a read of
 * every platform it is on, paced like a person; with enough bands the night runs out, and a loop
 * that always starts at the first one would check the first few for ever and the last ones never —
 * silently, because each band's result is kept separately and nobody is told that theirs was not
 * reached.
 */
class PlatformCheckScheduleTest {

    private static final Duration A_BAND_TAKES = Duration.ofMinutes(20);
    private static final Duration BUDGET = Duration.ofHours(1);

    private Instant now = Instant.parse("2026-10-13T04:30:00Z");
    private final List<BandId> checked = new ArrayList<>();
    private final List<BandId> bands = new ArrayList<>();

    private final Clock clock = new Clock() {
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    };

    /** Only {@code check} is used here; the rest of the port is the page's. */
    private abstract static class Checks implements CheckPlatformsUseCase {
        @Override
        public Optional<PlatformCheck> lastCheck(BandId band) {
            return Optional.empty();
        }

        @Override
        public boolean running(BandId band) {
            return false;
        }

        @Override
        public void forget(BandId band, Platform platform, GigId gig) {
        }

        @Override
        public void dismiss(BandId band, Platform platform, GigId gig) {
        }
    }

    private final CheckPlatformsUseCase check = new Checks() {
        @Override
        public Optional<PlatformCheck> check(BandId band) {
            checked.add(band);
            now = now.plus(A_BAND_TAKES);                 // reading a band's platforms takes a while
            return Optional.empty();
        }
    };

    private final BandDirectory directory = new BandDirectory() {
        @Override
        public List<BandId> all() {
            return List.copyOf(bands);
        }

        @Override
        public Optional<Band> bySlug(String slug) {
            return Optional.empty();
        }
    };

    private final PlatformCheckSchedule schedule = new PlatformCheckSchedule(check, directory,
            new CheckProperties(false, null, null, BUDGET), clock);

    private BandId band(String name) {
        BandId band = new BandId(UUID.nameUUIDFromBytes(name.getBytes()));
        bands.add(band);
        return band;
    }

    @Test
    @DisplayName("every band is checked when the night is long enough")
    void every_band_is_checked_when_the_night_is_long_enough() {
        List<BandId> all = List.of(band("one"), band("two"));
        schedule.round(all, now.plus(BUDGET));
        assertThat(checked).isEqualTo(all);
    }

    @Test
    @DisplayName("the night runs out, and tomorrow starts where it stopped")
    void the_night_runs_out_and_tomorrow_starts_where_it_stopped() {
        List<BandId> all = List.of(band("one"), band("two"), band("three"), band("four"), band("five"));

        schedule.round(all, now.plus(BUDGET));            // an hour: three bands of twenty minutes
        assertThat(checked).containsExactly(all.get(0), all.get(1), all.get(2));

        checked.clear();
        schedule.round(all, now.plus(BUDGET));
        assertThat(checked).as("the two that were not reached go first")
                .containsExactly(all.get(3), all.get(4), all.get(0));

        checked.clear();
        schedule.round(all, now.plus(BUDGET));
        assertThat(checked).containsExactly(all.get(1), all.get(2), all.get(3));
    }

    @Test
    @DisplayName("a band whose check fails costs that band's turn and no other's")
    void a_band_whose_check_fails_costs_that_bands_turn_and_no_others() {
        List<BandId> all = List.of(band("one"), band("two"));
        PlatformCheckSchedule breaking = new PlatformCheckSchedule(new Checks() {
            @Override
            public Optional<PlatformCheck> check(BandId band) {
                checked.add(band);
                throw new IllegalStateException("the platform said something unexpected");
            }
        }, directory, new CheckProperties(false, null, null, BUDGET), clock);

        breaking.round(all, now.plus(BUDGET));
        assertThat(checked).isEqualTo(all);
    }

    @Test
    @DisplayName("no bands at all is a round, not a crash")
    void no_bands_at_all_is_a_round_not_a_crash() {
        schedule.round(List.of(), now.plus(BUDGET));
        assertThat(checked).isEmpty();
    }
}
