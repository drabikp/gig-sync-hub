package sk.drabikp.gigsynchub.gig.application.port.out;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.gig.domain.Gig;
import sk.drabikp.gigsynchub.gig.domain.GigCursor;
import sk.drabikp.gigsynchub.gig.domain.GigId;
import sk.drabikp.gigsynchub.gig.domain.GigQuery;
import sk.drabikp.gigsynchub.gig.domain.GigRow;
import sk.drabikp.gigsynchub.gig.domain.GigSlice;
import sk.drabikp.gigsynchub.gig.domain.TestGigs;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static sk.drabikp.gigsynchub.gig.domain.TestBands.BAND;
import static sk.drabikp.gigsynchub.gig.domain.TestBands.OTHER;

/**
 * What a page of the gig list means — asked of EVERY gig store, the database's and any in-memory one,
 * so that the list cannot read one way in a test and another on the page. Each store brings its own
 * {@link #repository()}.
 *
 * <p>These are the list's promises: it filters, orders and pages where the rows are, it never shows
 * another band's gig, a narrowing that nothing satisfies shows nothing, and walking the cursor sees
 * every row exactly once.
 */
public abstract class GigPagingContract {

    protected static final ZoneId ZONE = ZoneId.of("Europe/Prague");
    protected static final LocalDate TODAY = LocalDate.of(2026, 10, 11);

    protected abstract GigRepository repository();

    /**
     * Each promise is tried on an empty catalog and leaves one behind, saying so itself rather than
     * leaning on a store's transactions — the in-memory one has none to roll back, and a test whose
     * rows outlive it is how a suite starts depending on the order it runs in.
     */
    @BeforeEach
    @AfterEach
    void anEmptyCatalog() {
        for (BandId band : List.of(BAND, OTHER)) {
            repository().findAll(band).forEach(gig -> repository().deleteById(band, gig.id()));
        }
    }

    protected ZonedDateTime at(int year, int month, int day, int hour) {
        return ZonedDateTime.of(year, month, day, hour, 0, 0, 0, ZONE);
    }

    private Gig save(BandId band, String title, String venue, String city, ZonedDateTime start) {
        Gig gig = TestGigs.gig(title, venue, city, start);
        repository().save(band, gig);
        return gig;
    }

    private List<String> titles(GigSlice slice) {
        return slice.rows().stream().map(GigRow::title).toList();
    }

    @Test
    void upcoming_and_past_part_at_today_and_each_runs_away_from_it() {
        save(BAND, "last year", "Klub 007", "Praha", at(2025, 11, 20, 20));
        save(BAND, "yesterday", "Barrák", "Ostrava", at(2026, 10, 10, 20));
        save(BAND, "today", "Fléda", "Brno", at(2026, 10, 11, 20));
        save(BAND, "next week", "Melodka", "Brno", at(2026, 10, 18, 20));

        assertThat(titles(repository().page(BAND, GigQuery.upcoming(), TODAY)))
                .as("what is to come, soonest first — today counts as to come")
                .containsExactly("today", "next week");
        assertThat(titles(repository().page(BAND, GigQuery.upcoming().of(GigQuery.Period.PAST), TODAY)))
                .as("what has been, latest first")
                .containsExactly("yesterday", "last year");
    }

    @Test
    void the_words_are_searched_where_the_rows_are_folded_so_accents_and_case_do_not_matter() {
        save(BAND, "Jesenné turné", "Barrák", "Ostrava", at(2026, 11, 20, 20));
        save(BAND, "Winter show", "Fléda", "Brno", at(2026, 11, 21, 20));

        assertThat(titles(repository().page(BAND, GigQuery.upcoming().matching("ostrava"), TODAY)))
                .as("the town, in plain letters").containsExactly("Jesenné turné");
        assertThat(titles(repository().page(BAND, GigQuery.upcoming().matching("FLÉDA"), TODAY)))
                .as("the venue, however it is typed").containsExactly("Winter show");
        assertThat(titles(repository().page(BAND, GigQuery.upcoming().matching("turne"), TODAY)))
                .as("the title without its accents").containsExactly("Jesenné turné");
        assertThat(repository().page(BAND, GigQuery.upcoming().matching("nothing here"), TODAY).rows()).isEmpty();
    }

    /**
     * A search means the characters it is made of. {@code %} and {@code _} are a {@code like}
     * pattern's own, and the folded text keeps them, so a store that passes them through answered a
     * search for a per-cent sign with the band's WHOLE catalog — and counted it that way in the
     * heading. Asked for here because only one of the two stores can get it wrong.
     */
    @Test
    void the_characters_a_like_pattern_uses_are_searched_for_themselves() {
        save(BAND, "100% live", "Barrák", "Ostrava", at(2026, 11, 20, 20));
        save(BAND, "Winter show", "Fléda", "Brno", at(2026, 11, 21, 20));

        assertThat(titles(repository().page(BAND, GigQuery.upcoming().matching("%"), TODAY)))
                .as("a per-cent sign finds the gig with one, not every gig").containsExactly("100% live");
        assertThat(titles(repository().page(BAND, GigQuery.upcoming().matching("100%"), TODAY)))
                .as("and reads as part of the words around it").containsExactly("100% live");
        assertThat(repository().page(BAND, GigQuery.upcoming().matching("_"), TODAY).rows())
                .as("an underscore stands for itself, not for any one character").isEmpty();
        assertThat(repository().page(BAND, GigQuery.upcoming().matching("w_nter"), TODAY).rows())
                .as("so it does not match a word that merely fits the pattern").isEmpty();
    }

    @Test
    void a_narrowing_to_no_gig_shows_none_and_one_to_some_shows_only_those() {
        Gig kept = save(BAND, "kept", "Klub 007", "Praha", at(2026, 11, 20, 20));
        save(BAND, "left out", "Fléda", "Brno", at(2026, 11, 21, 20));

        assertThat(titles(repository().page(BAND, GigQuery.upcoming().onlyIn(Set.of(kept.id())), TODAY)))
                .containsExactly("kept");
        assertThat(repository().page(BAND, GigQuery.upcoming().onlyIn(Set.of()), TODAY).rows())
                .as("a filter that matched nothing shows nothing — never everything").isEmpty();
    }

    /**
     * The other way round: "missing from some platform" is not a set of gigs the publications can
     * name — it is every gig BUT the ones already everywhere, which they can. Both narrowings, and
     * the cancelled flag the same filter carries, belong to the store.
     */
    @Test
    void everything_but_some_gigs_can_be_asked_for_too_cancelled_ones_set_aside() {
        Gig everywhere = save(BAND, "already out", "Klub 007", "Praha", at(2026, 11, 20, 20));
        save(BAND, "not yet", "Fléda", "Brno", at(2026, 11, 21, 20));
        Gig on = TestGigs.gig("called off", "Melodka", "Brno", at(2026, 11, 22, 20));
        repository().save(BAND, new Gig(on.id(), on.title(), on.schedule(), on.location(), on.lineup(),
                on.admission(), on.description(), on.facebookUrl(), on.ticketUrl(), on.posterImageUrl(), true));

        assertThat(titles(repository().page(BAND, GigQuery.upcoming()
                .excluding(Set.of(everywhere.id())).cancelled(false), TODAY)))
                .containsExactly("not yet");
        assertThat(titles(repository().page(BAND, GigQuery.upcoming().excluding(Set.of()), TODAY)))
                .as("excluding nothing leaves every gig in")
                .containsExactly("already out", "not yet", "called off");
        assertThat(titles(repository().page(BAND, GigQuery.upcoming().cancelled(true), TODAY)))
                .containsExactly("called off");
    }

    @Test
    void the_cursor_walks_every_row_once_and_the_total_counts_them_all() {
        for (int day = 12; day <= 21; day++) {
            save(BAND, "gig " + day, "Klub 007", "Praha", at(2026, 10, day, 20));
        }

        List<String> walked = new ArrayList<>();
        GigQuery query = GigQuery.upcoming().sized(3);
        GigCursor cursor = null;
        int pages = 0;
        do {
            GigSlice slice = repository().page(BAND, cursor == null ? query : query.from(cursor), TODAY);
            assertThat(slice.total()).as("the total is what matches, not what the page holds").isEqualTo(10);
            assertThat(slice.rows()).hasSizeLessThanOrEqualTo(3);
            walked.addAll(titles(slice));
            cursor = slice.next();
            pages++;
        } while (cursor != null && pages < 10);

        assertThat(walked).as("every row once, in order")
                .containsExactly("gig 12", "gig 13", "gig 14", "gig 15", "gig 16",
                        "gig 17", "gig 18", "gig 19", "gig 20", "gig 21");
        assertThat(pages).as("three at a time, then the last partial page ends it").isEqualTo(4);
    }

    @Test
    void two_gigs_at_the_same_moment_are_still_walked_once_each() {
        save(BAND, "aaa", "Klub 007", "Praha", at(2026, 11, 20, 20));
        save(BAND, "bbb", "Fléda", "Brno", at(2026, 11, 20, 20));

        GigSlice first = repository().page(BAND, GigQuery.upcoming().sized(1), TODAY);
        assertThat(first.rows()).hasSize(1);
        assertThat(first.next()).as("there is more").isNotNull();
        GigSlice second = repository().page(BAND, GigQuery.upcoming().sized(1).from(first.next()), TODAY);

        assertThat(titles(first)).isNotEqualTo(titles(second));
        assertThat(second.next()).as("and then no more").isNull();
    }

    @Test
    void a_page_holds_only_the_asking_bands_gigs() {
        save(BAND, "ours", "Klub 007", "Praha", at(2026, 11, 20, 20));
        save(OTHER, "theirs", "Klub 007", "Praha", at(2026, 11, 20, 20));

        assertThat(titles(repository().page(BAND, GigQuery.upcoming(), TODAY))).containsExactly("ours");
        assertThat(repository().page(BAND, GigQuery.upcoming(), TODAY).total()).isEqualTo(1);
        assertThat(titles(repository().page(OTHER, GigQuery.upcoming(), TODAY))).containsExactly("theirs");
    }

    @Test
    void a_row_carries_what_a_row_shows() {
        Gig gig = save(BAND, "Release show", "Klub 007", "Praha", at(2026, 11, 20, 21));

        GigRow row = repository().page(BAND, GigQuery.upcoming(), TODAY).rows().getFirst();
        assertThat(row.id()).isEqualTo(gig.id());
        assertThat(row.title()).isEqualTo("Release show");
        assertThat(row.venue()).isEqualTo("Klub 007");
        assertThat(row.city()).isEqualTo("Praha");
        assertThat(row.cancelled()).isFalse();
        assertThat(row.start()).as("the event's start, in the gig's own zone").isEqualTo(at(2026, 11, 20, 21));
        assertThat(row.showAt()).as("when the band plays, as the list orders by")
                .isEqualTo(at(2026, 11, 20, 21).toLocalDateTime());
    }

    @Test
    void a_band_cannot_ask_for_more_rows_than_the_list_gives() {
        assertThat(GigQuery.upcoming().sized(5_000).size()).isEqualTo(GigQuery.MAX_SIZE);
        assertThat(GigQuery.upcoming().sized(0).size()).isEqualTo(GigQuery.DEFAULT_SIZE);
    }

    @Test
    void a_cursor_that_is_not_ours_is_refused_rather_than_read_as_the_beginning() {
        assertThat(GigCursor.decode(null)).isNull();
        assertThat(GigCursor.decode("")).isNull();
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> GigCursor.decode("nonsense")))
                .isInstanceOf(IllegalArgumentException.class);
        GigCursor cursor = new GigCursor(at(2026, 11, 20, 21).toLocalDateTime(), GigId.fresh());
        assertThat(GigCursor.decode(cursor.encoded())).isEqualTo(cursor);
    }

    /** An identity no gig has, so a store can be asked about one it does not hold. */
    protected static GigId idOf(String key) {
        return TestGigs.id(key);
    }
}
