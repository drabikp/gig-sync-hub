package sk.drabikp.bzscraper.places.application;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.gig.domain.Coordinates;
import sk.drabikp.bzscraper.gig.domain.Country;
import sk.drabikp.bzscraper.places.domain.Town;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceServiceTest {

    private static final Town HRANICE_PREROV = new Town("Hranice", "okres Přerov", "Olomoucký kraj", "753 01",
            Country.CZECHIA, new Coordinates(49.548, 17.735));
    private static final Town HRANICE_CHEB = new Town("Hranice", "okres Cheb", "Karlovarský kraj", "351 24",
            Country.CZECHIA, new Coordinates(50.305, 12.176));

    private final List<String> asked = new ArrayList<>();
    private final PlaceService service = new PlaceService(text -> {
        asked.add(text);
        return List.of(HRANICE_PREROV, HRANICE_CHEB);
    });

    @Test
    void a_town_is_resolved_only_when_one_fits_name_country_and_postal_area() {
        assertThat(service.resolve("Hranice", Country.CZECHIA, "753 01")).contains(HRANICE_PREROV);
        assertThat(service.resolve("Hranice", Country.CZECHIA, null)).as("two fit: no guess").isEmpty();
        assertThat(service.resolve("Hranice", Country.SLOVAKIA, "753 01")).isEmpty();
    }

    @Test
    void no_name_asks_nothing_and_a_search_is_the_place_searchs_answer() {
        assertThat(service.resolve(" ", Country.CZECHIA, "753 01")).isEmpty();
        assertThat(asked).isEmpty();

        assertThat(service.search("Hranice")).containsExactly(HRANICE_PREROV, HRANICE_CHEB);
        assertThat(asked).containsExactly("Hranice");
    }
}
