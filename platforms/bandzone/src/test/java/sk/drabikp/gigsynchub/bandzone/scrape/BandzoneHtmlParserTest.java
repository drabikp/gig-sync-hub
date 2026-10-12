package sk.drabikp.gigsynchub.bandzone.scrape;

import org.jsoup.Jsoup;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BandzoneHtmlParserTest {

    private static final ZoneId PRAGUE = ZoneId.of("Europe/Prague");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 8);

    private static ZonedDateTime at(int y, int m, int d, int h) {
        return ZonedDateTime.of(y, m, d, h, 0, 0, 0, PRAGUE);
    }

    @Test
    void an_end_rendered_with_todays_date_is_the_end_time_of_the_gigs_own_night() {
        assertThat(BandzoneHtmlParser.plausibleEnd(at(2020, 10, 31, 19), at(2026, 10, 8, 22), TODAY))
                .isEqualTo(at(2020, 10, 31, 22));
        assertThat(BandzoneHtmlParser.plausibleEnd(at(2020, 10, 31, 19), at(2026, 10, 8, 2), TODAY))
                .as("past midnight").isEqualTo(at(2020, 11, 1, 2));
    }

    @Test
    void parts_bandzone_leaves_out_are_empty_and_a_gig_without_a_concert_link_is_dropped() {
        Elements articles = Jsoup.parse("""
                <article class="gig gig--canceled">
                  <a class="gig__link" href="/koncert/563380-fest-2026/"></a>
                  <h3 class="gig__title">Fest 2026</h3>
                  <div itemprop="startDate"><time datetime="2026-11-20T20:00:00+01:00"></time></div>
                  <div itemprop="location"><strong title="Praha"></strong><span><span title="Klub 007"></span></span></div>
                </article>
                <article class="gig"><h3 class="gig__title">No link</h3></article>
                """).select("article.gig");

        List<GigSummary> gigs = new BandzoneHtmlParser(Clock.fixed(Instant.parse("2026-10-08T10:00:00Z"), PRAGUE))
                .parse(articles);

        assertThat(gigs).singleElement().satisfies(gig -> {
            assertThat(gig.bzId()).isEqualTo("563380");
            assertThat(gig.title()).isEqualTo("Fest 2026");
            assertThat(gig.city()).isEqualTo("Praha");
            assertThat(gig.venue()).isEqualTo("Klub 007");
            assertThat(gig.start()).isEqualTo(at(2026, 11, 20, 20));
            assertThat(gig.end()).isNull();
            assertThat(gig.bands()).isNull();
            assertThat(gig.entryFee()).isNull();
            assertThat(gig.isCancelled()).isTrue();
        });
    }

    @Test
    void real_ends_are_kept() {
        assertThat(BandzoneHtmlParser.plausibleEnd(at(2026, 8, 27, 12), at(2026, 8, 30, 0), TODAY))
                .isEqualTo(at(2026, 8, 30, 0));
        assertThat(BandzoneHtmlParser.plausibleEnd(at(2026, 10, 5, 12), at(2026, 10, 8, 22), TODAY))
                .as("a festival ending today").isEqualTo(at(2026, 10, 8, 22));
    }
}
