package sk.drabikp.gigsynchub.gig.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocationTest {

    @Test
    void city_is_required() {
        assertThatThrownBy(() -> new Location("Klub", null, Country.CZECHIA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Location("Klub", "  ", Country.CZECHIA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blank_venue_becomes_null_and_displays_as_TBA() {
        Location loc = new Location("  ", "Praha", Country.CZECHIA);
        assertThat(loc.venue()).isNull();
        assertThat(loc.displayVenue()).isEqualTo("TBA");
    }

    @Test
    void venue_and_city_are_trimmed() {
        Location loc = new Location("  Klub 007 ", "  Praha ", Country.CZECHIA);
        assertThat(loc.venue()).isEqualTo("Klub 007");
        assertThat(loc.city()).isEqualTo("Praha");
        assertThat(loc.displayVenue()).isEqualTo("Klub 007");
    }

    @Test
    void country_supplies_canonical_name_and_timezone() {
        Location cz = new Location("Klub", "Praha", Country.CZECHIA);
        assertThat(cz.countryName()).isEqualTo("Czechia");
        assertThat(cz.timezone()).isEqualTo("Europe/Prague");
    }

    @Test
    void unknown_country_yields_empty_name_and_timezone() {
        Location unknown = new Location("Hall", "Vienna", null);
        assertThat(unknown.countryName()).isEmpty();
        assertThat(unknown.timezone()).isEmpty();
    }
}
