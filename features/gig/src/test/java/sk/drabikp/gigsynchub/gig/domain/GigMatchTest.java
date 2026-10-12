package sk.drabikp.gigsynchub.gig.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which concert a copy is about — what two sources of the same gig have in common, and the one
 * thing a band cannot have twice. These were a gig's identity until 2026-10, so the rules are the
 * same rules; only what they decide has changed.
 */
class GigMatchTest {

    private static final ZonedDateTime EVENING =
            ZonedDateTime.of(2026, 9, 15, 20, 0, 0, 0, ZoneId.of("Europe/Prague"));

    private static Gig at(String venue, String city) {
        return Gig.create("Show", GigSchedule.startingAt(EVENING), new Location(venue, city, Country.CZECHIA),
                List.of(), Admission.free(), null, null, null, null);
    }

    @Test
    void the_venue_is_folded_so_the_same_place_typed_two_ways_is_one_concert() {
        assertThat(at("Klub 007", "Praha").match()).isEqualTo(at("  klub   007 ", "Praha").match());
        assertThat(at("Klub 007", "Praha").match())
                .isEqualTo(new GigMatch(LocalDate.of(2026, 9, 15), "klub 007"));
    }

    @Test
    void a_gig_without_a_venue_is_matched_by_its_city_so_two_such_days_stay_two_concerts() {
        assertThat(at(null, "Petřvald").match().venue()).isEqualTo("@petřvald");
        assertThat(at("TBA ", "Prešov").match()).isNotEqualTo(at(null, "Petřvald").match());
    }

    @Test
    void the_key_stands_for_the_same_match_even_for_a_venue_with_the_separator_in_it() {
        GigMatch match = new GigMatch(LocalDate.of(2026, 10, 1), "klub | 007");
        GigMatch byCity = new GigMatch(LocalDate.of(2026, 10, 2), "@hranice");

        assertThat(match.key()).isEqualTo("2026-10-01|klub | 007");
        assertThat(GigMatch.fromKey(match.key())).isEqualTo(match);
        assertThat(GigMatch.fromKey(byCity.key())).isEqualTo(byCity);
    }

    @Test
    void it_reads_as_a_concert_in_a_sentence() {
        assertThat(new GigMatch(LocalDate.of(2026, 10, 1), "klub 007").label()).isEqualTo("2026-10-01 · klub 007");
        assertThat(new GigMatch(LocalDate.of(2026, 10, 1), "@hranice").label()).isEqualTo("2026-10-01 · hranice");
    }
}
