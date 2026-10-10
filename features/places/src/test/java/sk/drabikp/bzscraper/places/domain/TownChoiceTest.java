package sk.drabikp.bzscraper.places.domain;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.gig.domain.Coordinates;
import sk.drabikp.bzscraper.gig.domain.Country;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TownChoiceTest {

    private static final Town HRANICE_PREROV = new Town("Hranice", "okres Přerov", "Olomoucký kraj", "753 01",
            Country.CZECHIA, new Coordinates(49.548, 17.735));
    private static final Town HRANICE_CHEB = new Town("Hranice", "okres Cheb", "Karlovarský kraj", "351 24",
            Country.CZECHIA, new Coordinates(50.305, 12.176));
    private static final Town KOSICE_SK = new Town("Košice", "okres Košice I", "Košický kraj", "041 26",
            Country.SLOVAKIA, new Coordinates(48.717, 21.25));
    private static final Town KOSICE_CZ = new Town("Košice", "okres Tábor", "Jihočeský kraj", "391 17",
            Country.CZECHIA, new Coordinates(49.325, 14.751));
    private static final List<Town> ALL = List.of(HRANICE_PREROV, HRANICE_CHEB, KOSICE_SK, KOSICE_CZ);

    @Test
    void the_country_or_the_district_tells_same_named_towns_apart() {
        assertThat(TownChoice.pick("Kosice", Country.SLOVAKIA, null, null, ALL)).contains(KOSICE_SK);
        assertThat(TownChoice.pick("Hranice", Country.CZECHIA, "Okres Přerov", null, ALL)).contains(HRANICE_PREROV);
    }

    @Test
    void the_postal_area_does_when_nothing_else_does() {
        assertThat(TownChoice.pick("Hranice", Country.CZECHIA, null, "753 01", ALL)).contains(HRANICE_PREROV);
        assertThat(TownChoice.pick("Košice", null, null, "040 01", ALL)).as("040 and 041 are one area").contains(KOSICE_SK);
    }

    @Test
    void no_guess_when_several_fit_or_none_does() {
        assertThat(TownChoice.pick("Hranice", Country.CZECHIA, null, null, ALL)).isEmpty();
        assertThat(TownChoice.pick("Hranice", Country.SLOVAKIA, null, null, ALL)).isEmpty();
        assertThat(TownChoice.pick("Hranice", Country.CZECHIA, "okres Jičín", null, ALL)).isEmpty();
    }
}
