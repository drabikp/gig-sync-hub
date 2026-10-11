package sk.drabikp.bzscraper.gig.domain;

import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;

/** Near enough to be the same town, or a gig that ended up somewhere else entirely. */
class SamePlaceTest {

    /** Hranice in the Přerov district, as the place search gives it. */
    private static final Address HRANICE = new Address(null, "753 01", "okres Přerov", "Olomoucký",
            49.5479, 17.7345);

    @Test
    void a_point_in_the_town_is_the_same_place() {
        assertThat(SamePlace.tooFarFrom(HRANICE, new Coordinates(49.5479, 17.7345))).isEmpty();
        assertThat(SamePlace.tooFarFrom(HRANICE, new Coordinates(49.56, 17.75))).as("across town").isEmpty();
    }

    @Test
    void the_other_hranice_is_not() {
        // Hranice u Aše, ~300 km away: the same name, the other end of the country
        OptionalDouble off = SamePlace.tooFarFrom(HRANICE, new Coordinates(50.3049, 12.1766));

        assertThat(off).isPresent();
        assertThat(off.getAsDouble()).isGreaterThan(300);
    }

    @Test
    void nothing_to_compare_is_not_a_difference() {
        Address typed = new Address(null, null, null, null, null, null);

        assertThat(SamePlace.tooFarFrom(typed, new Coordinates(50.3049, 12.1766)))
                .as("a town only typed has no point of its own").isEmpty();
        assertThat(SamePlace.tooFarFrom(null, new Coordinates(50.3049, 12.1766))).isEmpty();
        assertThat(SamePlace.tooFarFrom(HRANICE, null)).as("the platform didn't say where").isEmpty();
    }

    @Test
    void the_line_is_drawn_once_for_everyone_who_asks() {
        Coordinates town = new Coordinates(49.5479, 17.7345);
        // due north: a tenth of a degree of latitude is about 11 km
        double justInside = 49.5479 + (SamePlace.KILOMETRES - 2) / 111.0;
        double justOutside = 49.5479 + (SamePlace.KILOMETRES + 2) / 111.0;

        assertThat(SamePlace.tooFarFrom(HRANICE, new Coordinates(justInside, 17.7345))).isEmpty();
        assertThat(SamePlace.tooFarFrom(HRANICE, new Coordinates(justOutside, 17.7345))).isPresent();
        assertThat(town.kilometresTo(new Coordinates(justOutside, 17.7345))).isGreaterThan(SamePlace.KILOMETRES);
    }
}
