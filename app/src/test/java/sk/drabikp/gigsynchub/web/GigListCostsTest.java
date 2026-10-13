package sk.drabikp.gigsynchub.web;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import sk.drabikp.gigsynchub.catalog.application.port.in.SaveGigUseCase;
import sk.drabikp.gigsynchub.gig.domain.Admission;
import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.gig.domain.Country;
import sk.drabikp.gigsynchub.gig.domain.Gig;
import sk.drabikp.gigsynchub.gig.domain.GigSchedule;
import sk.drabikp.gigsynchub.gig.domain.Location;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What a page of the gig list COSTS, counted rather than assumed — the first thing to measure, and the
 * only way an N+1 is noticed here rather than by a band with a full catalog.
 *
 * <p>The promise is not a particular number of statements (the first request of a run pays for warming
 * up, and the access check and the sign-in are every request's) but that the number does not FOLLOW
 * the rows: twenty rows must cost what five do, and the second page what the first did. A query per
 * row, or a list that reads the whole catalog to show a page of it, breaks that at once.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GigListCostsTest {

    private static final String GIGS = "/api/bands/" + BandId.FIRST + "/gigs";

    /** Seeded once: the app's tests share one database, and these gigs are only ever read here. */
    private static boolean seeded;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SaveGigUseCase catalog;

    @Autowired
    private SessionFactory sessions;

    private Statistics statistics;

    @BeforeEach
    void aCatalogOfSixtyGigs() {
        if (!seeded) {
            for (int day = 1; day <= 60; day++) {
                catalog.add(BandId.FIRST, gig("cost gig " + day, day));
            }
            seeded = true;
        }
        statistics = sessions.getStatistics();
        statistics.setStatisticsEnabled(true);
    }

    private static Gig gig(String title, int day) {
        return Gig.create(title, GigSchedule.startingAt(
                        ZonedDateTime.of(2027, 1, 1, 20, 0, 0, 0, ZoneId.of("Europe/Prague")).plusDays(day)),
                new Location("Klub 007", "Praha", Country.CZECHIA), List.of(), Admission.free(),
                null, null, null, null);
    }

    /** The statements one request runs, with the run already warm. */
    private long statementsFor(String query) throws Exception {
        statistics.clear();
        mvc.perform(get(GIGS + query).with(SignedIn.owner())).andExpect(status().isOk());
        return statistics.getPrepareStatementCount();
    }

    @Test
    void a_page_costs_what_a_page_costs_however_many_rows_it_holds() throws Exception {
        statementsFor("?size=5");                                     // warm up
        long five = statementsFor("?size=5");
        long twenty = statementsFor("?size=20");
        long fifty = statementsFor("?size=50");

        assertThat(twenty).as("twenty rows cost what five do — nothing is read per row").isEqualTo(five);
        assertThat(fifty).as("and fifty the same").isEqualTo(five);

        // the entities a request loads are the access check's, the sign-in's and the open work's —
        // never one per row: a list reads ROWS (a projection), which the session need not watch
        statistics.clear();
        mvc.perform(get(GIGS + "?size=5").with(SignedIn.owner()));
        long entitiesForFive = statistics.getEntityLoadCount();
        statistics.clear();
        mvc.perform(get(GIGS + "?size=50").with(SignedIn.owner()));

        assertThat(statistics.getEntityLoadCount()).as("ten times the rows, the same entities")
                .isEqualTo(entitiesForFive);
    }

    @Test
    void the_second_page_costs_LESS_than_the_first_and_holds_the_rows_that_follow() throws Exception {
        statementsFor("?size=20");
        long first = statementsFor("?size=20");
        String cursor = cursorOf(mvc.perform(get(GIGS + "?size=20").with(SignedIn.owner()))
                .andReturn().getResponse().getContentAsString());

        long second = statementsFor("?size=20&after=" + cursor);

        assertThat(second).as("keyset paging: the hundredth page is the same work as the first, LESS the "
                        + "count — which is the first page's alone, the page having kept it")
                .isEqualTo(first - 1);
        mvc.perform(get(GIGS + "?size=20&after=" + cursor).with(SignedIn.owner()))
                .andExpect(jsonPath("$.gigs.length()").value(20))
                .andExpect(jsonPath("$.gigs[0].title").value("cost gig 21"))
                .andExpect(jsonPath("$.total").doesNotExist());
        mvc.perform(get(GIGS + "?size=20").with(SignedIn.owner()))
                .andExpect(jsonPath("$.total").value(60));
    }

    @Test
    void the_filters_are_the_servers_work_and_cost_no_read_per_row() throws Exception {
        statementsFor("?size=20");
        long plain = statementsFor("?size=20");

        assertThat(statementsFor("?size=20&q=klub")).as("a search is a predicate, not another query")
                .isEqualTo(plain);
        assertThat(statementsFor("?size=20&q=klub&period=past"))
                .as("nor is the other side of today — and an empty page asks even less, having no rows to enrich")
                .isLessThanOrEqualTo(plain);
        assertThat(statementsFor("?size=20&show=needs"))
                .as("the gigs a platform failed on come out of work the page already read — and when "
                        + "nothing failed, the store is not asked at all")
                .isLessThanOrEqualTo(plain);
        assertThat(statementsFor("?size=20&show=publishable"))
                .as("\"not on every platform yet\" is a count inside the page's own query. It used to be "
                        + "one read MORE — the band's whole publication table, turned into a `not in` of "
                        + "every gig that is already everywhere, which grows with the catalog and is "
                        + "exactly what this test exists to catch")
                .isEqualTo(plain);
    }

    @Test
    void the_words_narrow_the_page_in_the_database() throws Exception {
        mvc.perform(get(GIGS).param("q", "cost gig 7").with(SignedIn.owner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.gigs[0].title").value("cost gig 7"));
        mvc.perform(get(GIGS).param("q", "nothing like this").with(SignedIn.owner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.gigs.length()").value(0))
                .andExpect(jsonPath("$.cursor").doesNotExist());
    }

    /**
     * A bulk action reads the gigs it was given in ONE go. Selecting them was a {@code findById} per
     * gig, in one transaction — fine for the three a person clicks, a thousand round trips for a
     * "select all" on a full catalog, which the spec now allows up to.
     */
    @Test
    void publishing_a_selection_reads_the_gigs_in_one_go() throws Exception {
        List<String> tokens = tokensOfTheFirst(20);

        statistics.clear();
        mvc.perform(post(GIGS + "/publish").with(SignedIn.owner()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gigs\": [\"" + String.join("\",\"", tokens.subList(0, 3)) + "\"]}"))
                .andExpect(status().isOk());
        long three = statistics.getPrepareStatementCount();

        statistics.clear();
        mvc.perform(post(GIGS + "/publish").with(SignedIn.owner()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gigs\": [\"" + String.join("\",\"", tokens) + "\"]}"))
                .andExpect(status().isOk());
        long twenty = statistics.getPrepareStatementCount();

        assertThat(twenty - three).as("three gigs cost %d statements and twenty cost %d: the READING of "
                        + "them must not follow their number", three, twenty)
                .isLessThan(20 - 3);
    }

    /** The cursor out of a list's answer — found, not matched: ".*x.*" over a page of JSON is
     * three greedy runs handing characters back to one another. */
    private static String cursorOf(String body) {
        Matcher cursor = Pattern.compile("\"cursor\":\"([^\"]+)\"").matcher(body);
        assertThat(cursor.find()).as("the page says where to read on from").isTrue();
        return cursor.group(1);
    }

    private List<String> tokensOfTheFirst(int howMany) throws Exception {
        String body = mvc.perform(get(GIGS + "?size=" + howMany).with(SignedIn.owner()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> tokens = new ArrayList<>();
        Matcher ids = Pattern.compile("\"id\":\"([^\"]+)\"").matcher(body);
        while (ids.find()) {
            tokens.add(ids.group(1));
        }
        return tokens;
    }

    @Test
    void a_cursor_from_somewhere_else_is_refused() throws Exception {
        mvc.perform(get(GIGS + "?after=not-a-cursor").with(SignedIn.owner()))
                .andExpect(status().isBadRequest());
    }
}
