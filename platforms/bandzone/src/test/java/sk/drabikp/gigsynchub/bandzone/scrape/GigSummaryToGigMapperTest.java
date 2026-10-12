package sk.drabikp.gigsynchub.bandzone.scrape;

import org.junit.jupiter.api.Test;
import sk.drabikp.gigsynchub.gig.domain.Country;
import sk.drabikp.gigsynchub.gig.domain.EntryType;
import sk.drabikp.gigsynchub.gig.domain.Gig;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GigSummaryToGigMapperTest {

    private static final ZonedDateTime START =
            ZonedDateTime.of(2026, 9, 15, 20, 0, 0, 0, ZoneId.of("Europe/Prague"));

    private static GigSummary.GigSummaryBuilder base() {
        return GigSummary.GigSummaryBuilder.aGigSummary()
                .setTitle("Show").setStart(START).setVenue("Klub 007").setBands(List.of("Eufory"));
    }

    private static Gig map(GigSummary summary) {
        return GigSummaryToGigMapper.toGig(summary).orElseThrow();
    }

    @Test
    void parses_czech_city_and_country_and_carries_core_fields() {
        Gig gig = map(base().setCity("Hořice, ČR").build());

        assertThat(gig.location().city()).isEqualTo("Hořice");
        assertThat(gig.location().country()).isEqualTo(Country.CZECHIA);
        assertThat(gig.title()).isEqualTo("Show");
        assertThat(gig.location().venue()).isEqualTo("Klub 007");
        assertThat(gig.lineup()).containsExactly("Eufory");
    }

    @Test
    void parses_slovak_city_and_country() {
        Gig gig = map(base().setCity("Martin, SK").build());

        assertThat(gig.location().city()).isEqualTo("Martin");
        assertThat(gig.location().country()).isEqualTo(Country.SLOVAKIA);
    }

    @Test
    void unknown_country_suffix_leaves_country_null_and_city_unchanged() {
        Gig gig = map(base().setCity("Vienna").build());

        assertThat(gig.location().city()).isEqualTo("Vienna");
        assertThat(gig.location().country()).isNull();
    }

    @Test
    void blank_or_zdarma_entry_fee_is_free_otherwise_paid() {
        assertThat(map(base().setCity("Praha, ČR").setEntryFee(null).build()).admission().type())
                .isEqualTo(EntryType.FREE);
        assertThat(map(base().setCity("Praha, ČR").setEntryFee("zdarma").build()).admission().type())
                .isEqualTo(EntryType.FREE);
        Gig paid = map(base().setCity("Praha, ČR").setEntryFee("150 Kč").build());
        assertThat(paid.admission().type()).isEqualTo(EntryType.PAID);
        assertThat(paid.admission().amount()).isEqualTo("150 Kč");
    }

    @Test
    void a_scraped_gig_without_the_pieces_the_aggregate_requires_maps_to_empty() {
        assertThat(GigSummaryToGigMapper.toGig(base().setCity("Praha, ČR").setStart(null).build()))
                .isEmpty(); // no start
        assertThat(GigSummaryToGigMapper.toGig(base().setCity(null).build()))
                .isEmpty(); // no city
        assertThat(GigSummaryToGigMapper.toGig(base().setCity("Praha, ČR").setTitle("  ").build()))
                .isEmpty(); // blank title
    }
}
