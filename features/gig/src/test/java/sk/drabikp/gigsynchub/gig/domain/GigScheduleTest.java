package sk.drabikp.gigsynchub.gig.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GigScheduleTest {

    private static ZonedDateTime at(int y, int m, int d, int h) {
        return ZonedDateTime.of(y, m, d, h, 0, 0, 0, ZoneId.of("Europe/Prague"));
    }

    @Test
    void a_start_only_schedule_is_valid() {
        GigSchedule s = GigSchedule.startingAt(at(2026, 9, 15, 20));
        assertThat(s.hasEnd()).isFalse();
        assertThat(s.startDate()).isEqualTo(LocalDate.of(2026, 9, 15));
    }

    @Test
    void start_is_required() {
        assertThatThrownBy(() -> new GigSchedule(null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void end_must_not_be_before_start() {
        assertThatThrownBy(() -> new GigSchedule(at(2026, 9, 15, 20), at(2026, 9, 15, 19)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void end_equal_to_or_after_start_is_valid() {
        assertThat(new GigSchedule(at(2026, 9, 15, 20), at(2026, 9, 15, 23)).hasEnd()).isTrue();
    }

    @Test
    void a_festival_over_several_days_keeps_its_length_and_the_bands_slot() {
        GigSchedule festival = new GigSchedule(at(2026, 8, 27, 0), at(2026, 8, 30, 0),
                new Slot(at(2026, 8, 28, 19).plusMinutes(30), at(2026, 8, 28, 21)));

        assertThat(festival.multiDay()).isTrue();
        assertThat(festival.days()).as("ends at midnight: the 29th is the last day")
                .containsExactly(LocalDate.of(2026, 8, 27), LocalDate.of(2026, 8, 28), LocalDate.of(2026, 8, 29));
        assertThat(festival.covers(LocalDate.of(2026, 8, 28))).isTrue();
        assertThat(festival.covers(LocalDate.of(2026, 8, 30))).isFalse();
        assertThat(festival.showStart()).isEqualTo(at(2026, 8, 28, 19).plusMinutes(30));
        assertThat(festival.showEnd()).isEqualTo(at(2026, 8, 28, 21));
    }

    @Test
    void without_a_slot_the_band_plays_at_the_events_times() {
        GigSchedule night = new GigSchedule(at(2026, 9, 15, 20), at(2026, 9, 16, 2));

        assertThat(night.multiDay()).as("a night into the small hours").isTrue();
        assertThat(night.showStart()).isEqualTo(night.start());
        assertThat(night.showEnd()).isEqualTo(night.end());
        assertThat(GigSchedule.startingAt(at(2026, 9, 15, 20)).multiDay()).isFalse();
    }

    @Test
    void the_slot_lies_within_the_event() {
        assertThatThrownBy(() -> new GigSchedule(at(2026, 8, 27, 12), at(2026, 8, 29, 23),
                new Slot(at(2026, 8, 30, 20), null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GigSchedule(at(2026, 8, 27, 12), null,
                new Slot(at(2026, 8, 27, 11), null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Slot(at(2026, 8, 27, 21), at(2026, 8, 27, 20)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void an_event_lasting_years_is_bad_data_and_covers_only_its_first_two_weeks() {
        GigSchedule broken = new GigSchedule(at(2020, 10, 31, 19), at(2026, 10, 8, 22));

        assertThat(broken.covers(LocalDate.of(2020, 11, 2))).isTrue();
        assertThat(broken.covers(LocalDate.of(2026, 8, 28))).isFalse();
        assertThat(broken.days()).hasSize(14);
    }
}
