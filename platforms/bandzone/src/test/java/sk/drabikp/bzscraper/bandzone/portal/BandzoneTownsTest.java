package sk.drabikp.bzscraper.bandzone.portal;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.bandzone.portal.BandzoneTowns.Suggestion;
import sk.drabikp.bzscraper.gig.domain.Country;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Suggestions as Bandzone's city search returns them (probed on the test band). */
class BandzoneTownsTest {

    private static final List<Suggestion> KOSICE = List.of(
            new Suggestion(0, "Kosice", "okres Hradec Králové, kraj Královéhradecký"),
            new Suggestion(1, "Košice", "okres Kutná Hora, kraj Středočeský"),
            new Suggestion(2, "Košice", "okres Tábor, kraj Jihočeský"),
            new Suggestion(3, "Košice", "okres Košice I, kraj Košický, Slovensko"),
            new Suggestion(4, "Košická Belá", "okres Košice - okolie, kraj Košický, Slovensko"));
    private static final List<Suggestion> HRANICE = List.of(
            new Suggestion(0, "Hranice", "okres Přerov, kraj Olomoucký"),
            new Suggestion(1, "Hranice", "okres České Budějovice, kraj Jihočeský"),
            new Suggestion(2, "Hranice", "okres Cheb, kraj Karlovarský"),
            new Suggestion(3, "Hranice I-Město, Hranice", "okres Přerov, kraj Olomoucký"));

    @Test
    void the_country_picks_the_slovak_city_over_czech_villages_of_the_same_name() {
        assertThat(BandzoneTowns.choose("Košice", Country.SLOVAKIA, null, KOSICE).pick().index()).isEqualTo(3);
    }

    @Test
    void the_district_picks_among_towns_of_one_country() {
        assertThat(BandzoneTowns.choose("Hranice", Country.CZECHIA, "okres Přerov", HRANICE).pick().index())
                .isEqualTo(0);
    }

    @Test
    void several_fits_or_none_are_explained_never_guessed() {
        BandzoneTowns.Choice several = BandzoneTowns.choose("Hranice", Country.CZECHIA, null, HRANICE);
        BandzoneTowns.Choice none = BandzoneTowns.choose("Staré Mesto", Country.SLOVAKIA, null, KOSICE);

        assertThat(several.pick()).isNull();
        assertThat(several.problem()).contains("several towns named Hranice", "okres Přerov", "okres Cheb",
                "pick the town from the list");
        assertThat(none.pick()).isNull();
        assertThat(none.problem()).contains("doesn't know the town Staré Mesto");
    }
}
