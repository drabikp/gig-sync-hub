package sk.drabikp.gigsynchub.gig.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** City spellings seen on Bandzone vs Bandsintown for the same gigs (live data, 2026-10-07). */
class CityNameTest {

    @Test
    void english_names_match_the_local_ones() {
        assertThat(CityName.same("Prague", "Praha")).isTrue();
        assertThat(CityName.same("Pilsen", "Plzeň")).isTrue();
    }

    @Test
    void accents_case_spacing_and_a_district_number_are_ignored() {
        assertThat(CityName.same("Vsetín 1", "Vsetín")).isTrue();
        assertThat(CityName.same("Stará Turá", "stara  tura")).isTrue();
        assertThat(CityName.same("Praha 7", "Prague")).isTrue();
    }

    @Test
    void different_cities_differ() {
        assertThat(CityName.same("Petřvald", "Prešov")).isFalse();
        assertThat(CityName.same("Brno", "Praha")).isFalse();
    }
}
