package sk.drabikp.gigsynchub.bandzone.scrape;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Gigs as a band page's data lists them (shapes recorded from the real site, 2026-11-22). */
class BandzoneHtmlParserTest {

    private static final ZoneId PRAGUE = ZoneId.of("Europe/Prague");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static ZonedDateTime at(int y, int m, int d, int h, int min) {
        return ZonedDateTime.of(y, m, d, h, min, 0, 0, PRAGUE);
    }

    @Test
    void reads_a_gig_with_prague_times_and_drops_one_without_its_concert_id() throws Exception {
        List<JsonNode> gigs = List.of(JSON.readTree("""
                {"gigId":562946,"title":"Underfolk u Niklíčků ve stodole","dateFrom":"2026-09-18T19:00:00.000Z",
                 "dateUntil":"2026-09-18T23:30:00.000Z","venueId":12,"venueName":"Stodola","venueText":"Stodola",
                 "cityName":"Hranice","ticketPriceText":"150 Kč","statusId":7,
                 "slug":"2026-09-18-band-stodola"}"""), JSON.readTree("""
                {"gigId":null,"title":"No id","dateFrom":"2026-09-19T19:00:00.000Z"}"""));

        assertThat(new BandzoneHtmlParser().parse(gigs)).singleElement().satisfies(gig -> {
            assertThat(gig.bzId()).isEqualTo("562946");
            assertThat(gig.url()).isEqualTo("/koncert/562946-2026-09-18-band-stodola");
            assertThat(gig.title()).isEqualTo("Underfolk u Niklíčků ve stodole");
            assertThat(gig.start()).as("the wall clock, not UTC").isEqualTo(at(2026, 9, 18, 19, 0));
            assertThat(gig.end()).isEqualTo(at(2026, 9, 18, 23, 30));
            assertThat(gig.city()).isEqualTo("Hranice");
            assertThat(gig.venue()).isEqualTo("Stodola");
            assertThat(gig.entryFee()).isEqualTo("150 Kč");
            assertThat(gig.isCancelled()).as("played is not cancelled").isFalse();
        });
    }

    @Test
    void parts_bandzone_leaves_out_are_empty_and_a_cancelled_gig_is_cancelled() throws Exception {
        List<JsonNode> gigs = List.of(JSON.readTree("""
                {"gigId":563380,"title":"Fest 2026","dateFrom":"$D2026-11-20T00:00:00.000Z",
                 "dateUntil":"$D2026-11-20T00:00:00.000Z","venueId":null,"venueName":null,"venueText":"Klub 007",
                 "cityName":"Praha","ticketPriceText":null,"statusId":3,"slug":"2026-11-20-fest"}"""));

        assertThat(new BandzoneHtmlParser().parse(gigs)).singleElement().satisfies(gig -> {
            assertThat(gig.start()).isEqualTo(at(2026, 11, 20, 0, 0));
            assertThat(gig.end()).as("midnight of its own day is no end").isNull();
            assertThat(gig.venue()).as("typed when no club").isEqualTo("Klub 007");
            assertThat(gig.bands()).as("the list doesn't say who else plays").isNull();
            assertThat(gig.entryFee()).isNull();
            assertThat(gig.isCancelled()).isTrue();
        });
    }

    @Test
    void a_festival_keeps_its_last_day_and_an_end_before_the_start_is_none() {
        assertThat(BandzoneHtmlParser.end(at(2026, 8, 27, 12, 0), at(2026, 8, 30, 0, 0)))
                .isEqualTo(at(2026, 8, 30, 0, 0));
        assertThat(BandzoneHtmlParser.end(at(2026, 8, 27, 20, 0), at(2026, 8, 27, 18, 0))).isNull();
    }
}
