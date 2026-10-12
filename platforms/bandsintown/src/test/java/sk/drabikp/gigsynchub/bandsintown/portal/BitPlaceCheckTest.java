package sk.drabikp.gigsynchub.bandsintown.portal;

import org.junit.jupiter.api.Test;
import sk.drabikp.gigsynchub.gig.domain.Address;
import sk.drabikp.gigsynchub.gig.domain.Country;
import sk.drabikp.gigsynchub.gig.domain.Gig;
import sk.drabikp.gigsynchub.gig.domain.GigId;
import sk.drabikp.gigsynchub.gig.domain.Location;
import sk.drabikp.gigsynchub.gig.domain.TestGigs;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Where Bandsintown placed an event, against the gig's town (coordinates seen on 2026-10-08). */
class BitPlaceCheckTest {

    private static Gig in(Address address) {
        Gig gig = TestGigs.gig("Fest", "Zámecký klub");
        return new Gig(GigId.fresh(), "Fest", gig.schedule(), new Location("Zámecký klub", "Hranice", Country.CZECHIA, address),
                List.of(), gig.admission(), null, null, null, null, false);
    }

    private static final Address HRANICE_PREROV =
            new Address(null, "753 01", "okres Přerov", "Olomoucký kraj", 49.548, 17.735);

    @Test
    void a_place_far_from_the_town_is_flagged() {
        String note = BitPlaces.check(in(HRANICE_PREROV),
                BitEvent.of(Map.of("venue_latitude", 49.75568, "venue_longitude", 14.25012)));

        assertThat(note).contains("Bandsintown placed it", "km from Hranice (okres Přerov)", "check");
    }

    @Test
    void a_place_in_the_town_or_an_unknown_one_is_not() {
        assertThat(BitPlaces.check(in(HRANICE_PREROV),
                BitEvent.of(Map.of("venue_latitude", 49.5476, "venue_longitude", 17.7347)))).isNull();
        assertThat(BitPlaces.check(in(null),
                BitEvent.of(Map.of("venue_latitude", 49.75568, "venue_longitude", 14.25012)))).as("town only typed").isNull();
        assertThat(BitPlaces.check(in(HRANICE_PREROV), BitEvent.of(Map.of()))).isNull();
    }
}
