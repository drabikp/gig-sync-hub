package sk.drabikp.gigsynchub.calendar.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sk.drabikp.gigsynchub.calendar.application.port.out.BandProfileStore;
import sk.drabikp.gigsynchub.calendar.application.port.out.CalendarFeed;
import sk.drabikp.gigsynchub.calendar.domain.CalendarChange;
import sk.drabikp.gigsynchub.calendar.domain.CalendarOverview;
import sk.drabikp.gigsynchub.calendar.domain.CalendarRow;
import sk.drabikp.gigsynchub.calendar.domain.CatalogMatch;
import sk.drabikp.gigsynchub.calendar.domain.event.CalendarEvent;
import sk.drabikp.gigsynchub.calendar.domain.event.CalendarEventKind;
import sk.drabikp.gigsynchub.calendar.domain.event.CalendarEventStatus;
import sk.drabikp.gigsynchub.calendar.domain.rules.BandProfile;
import sk.drabikp.gigsynchub.calendar.domain.rules.ProfileRule;
import sk.drabikp.gigsynchub.calendar.domain.rules.RuleKind;
import sk.drabikp.gigsynchub.catalog.application.CatalogFakes;
import sk.drabikp.gigsynchub.catalog.application.port.in.CatalogErrorCode;
import sk.drabikp.gigsynchub.catalog.application.port.in.GigIdentityTakenException;
import sk.drabikp.gigsynchub.gig.application.BadRequestException;
import sk.drabikp.gigsynchub.gig.application.NotFoundException;
import sk.drabikp.gigsynchub.gig.application.port.out.LiveUpdates;
import sk.drabikp.gigsynchub.gig.domain.GigSchedule;
import sk.drabikp.gigsynchub.gig.domain.Gig;
import sk.drabikp.gigsynchub.gig.domain.TestGigs;
import sk.drabikp.gigsynchub.sync.application.SyncFakes;
import sk.drabikp.gigsynchub.sync.application.SyncRequests;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static sk.drabikp.gigsynchub.gig.domain.TestBands.BAND;
import static sk.drabikp.gigsynchub.gig.domain.TestBands.OTHER;

class CalendarReviewServiceTest {

    private static final ZoneId PRAGUE = ZoneId.of("Europe/Prague");
    private static final LocalDateTime FEST_DAY = LocalDateTime.of(2026, 11, 20, 16, 0);

    private final CalendarFeed feed = mock(CalendarFeed.class);
    private final BandProfileStore profiles = mock(BandProfileStore.class);
    private final CalendarFakes.Decisions decisions = new CalendarFakes.Decisions();
    private final CalendarFakes.Snapshots snapshots = new CalendarFakes.Snapshots();
    private final CalendarFakes.Links links = new CalendarFakes.Links();
    private final SyncFakes.Gigs gigs = new SyncFakes.Gigs();
    private final SyncFakes.MutableClock clock = new SyncFakes.MutableClock();
    private final SyncFakes.Outbox outbox = new SyncFakes.Outbox();
    private final SyncFakes.Published published = new SyncFakes.Published();
    private final SyncRequests requests = SyncFakes.requests(outbox, published, new SyncFakes.Signals(), clock);
    private final CalendarReviewService service = new CalendarReviewService(feed, profiles, decisions, snapshots,
            links, gigs, new SyncFakes.DirectTransactions(), clock, new BandProfile.Thresholds(4, -1, -4),
            LiveUpdates.NONE);
    private final CalendarCatalogService catalog = new CalendarCatalogService(service,
            CatalogFakes.writes(gigs, published, outbox, requests),
            CatalogFakes.catalog(gigs, published, outbox, requests), links, new SyncFakes.DirectTransactions(),
            LiveUpdates.NONE);

    @BeforeEach
    void rules() {
        when(profiles.rules(any())).thenReturn(List.of(
                ProfileRule.preset(RuleKind.TITLE_CONTAINS, "fest", 3),
                ProfileRule.preset(RuleKind.NOTES_LABEL, "showtime", 5),
                ProfileRule.preset(RuleKind.CATALOG_GIG_SAME_DAY, null, 2),
                ProfileRule.preset(RuleKind.CANCELLED_TITLE, "zrusene", 0),
                ProfileRule.preset(RuleKind.SHOWTIME_LABEL, "showtime", 0)));
    }

    private static CalendarEvent event(String id, String title, LocalDateTime start, String notes) {
        return new CalendarEvent(id, title, "Klub 007, 160 00 Praha 6, Česko", notes, start, start.plusHours(6),
                false, false, false, CalendarEventStatus.CONFIRMED, null);
    }

    private static CalendarEvent fest() {
        return event("fest", "Fest", FEST_DAY, "Showtime: 20:30");
    }

    private CalendarOverview read(CalendarEvent... events) throws Exception {
        when(feed.read(BAND)).thenReturn(List.of(events));
        clock.advance(Duration.ofMinutes(10));
        return service.read(BAND);
    }

    private static CalendarRow row(CalendarOverview overview, String id) {
        return overview.rows().stream().filter(r -> r.eventId().equals(id)).findFirst().orElseThrow();
    }

    private static Gig festGig() {
        return TestGigs.gig("Fest", "Klub 007", ZonedDateTime.of(FEST_DAY.toLocalDate().atTime(20, 30), PRAGUE));
    }

    @Test
    void classifies_with_the_stored_rules_the_catalogs_gig_days_and_the_users_verdicts() throws Exception {
        gigs.save(BAND, festGig());
        decisions.decide(BAND, "visit", CalendarEventKind.NOT_GIG);

        CalendarOverview overview = read(event("fest", "Fest", FEST_DAY, ""), event("visit", "Fest", FEST_DAY.plusDays(1), ""));

        assertThat(row(overview, "fest").classification().kind()).isEqualTo(CalendarEventKind.GIG); // +3 fest, +2 catalog
        assertThat(row(overview, "visit").classification().kind()).isEqualTo(CalendarEventKind.NOT_GIG);
        assertThat(row(overview, "visit").classification().decidedByUser()).isTrue();
        assertThat(overview.lastRead()).isEqualTo(clock.instant());
    }

    @Test
    void the_first_read_is_the_starting_point_and_later_reads_show_what_is_new_changed_or_gone() throws Exception {
        CalendarEvent rehearsal = event("reh", "Rehearsal", FEST_DAY.minusDays(3), "");
        CalendarOverview first = read(event("fest", "Fest", FEST_DAY, ""), rehearsal);
        assertThat(first.rows()).allSatisfy(r -> assertThat(r.change()).isNull());

        CalendarEvent club = event("club", "Club night", FEST_DAY.plusDays(7), "");
        CalendarOverview second = read(fest(), club);

        assertThat(row(second, "club").change().type()).isEqualTo(CalendarChange.Type.NEW);
        CalendarChange festChange = row(second, "fest").change();
        assertThat(festChange.type()).isEqualTo(CalendarChange.Type.CHANGED);
        assertThat(festChange.fields()).containsExactly(CalendarChange.Field.NOTES);
        assertThat(festChange.suggestedBefore()).as("the new notes made it a sure gig").isEqualTo(CalendarEventKind.UNSURE);
        assertThat(row(second, "reh").removed()).isTrue();
        assertThat(row(second, "reh").change().type()).isEqualTo(CalendarChange.Type.REMOVED);

        CalendarOverview third = read(fest(), club);
        assertThat(row(third, "fest").change()).as("unseen changes stay").isNotNull();

        service.seenAll(BAND);

        assertThat(service.overview(BAND).rows()).extracting(CalendarRow::eventId).containsExactlyInAnyOrder("fest", "club");
        assertThat(service.overview(BAND).rows()).allSatisfy(r -> assertThat(r.change()).isNull());
    }

    @Test
    void a_gig_is_added_to_the_catalog_from_the_calendar_and_stays_linked_to_its_event() throws Exception {
        read(fest());
        CalendarRow before = row(service.overview(BAND), "fest");
        assertThat(before.missingFromCatalog()).isTrue();
        assertThat(before.draft().showTime()).hasToString("20:30");

        catalog.addToCatalog(BAND, "fest", festGig());

        CalendarRow after = row(service.overview(BAND), "fest");
        assertThat(gigs.findById(BAND, festGig().id())).isPresent();
        assertThat(after.match().state()).isEqualTo(CatalogMatch.State.LINKED);
        assertThat(after.match().differences()).isEmpty();
        assertThatThrownBy(() -> catalog.addToCatalog(BAND, "other", festGig()))
                .isInstanceOf(GigIdentityTakenException.class).extracting("code").isEqualTo(CatalogErrorCode.GIG_IDENTITY_TAKEN);
        assertThat(links.all(BAND)).doesNotContainKey("other");
    }

    /**
     * The review reads the gigs around the calendar's own days, not the band's whole history — a
     * band that has been playing for ten years must not pay for ten years to look at this month.
     * What has to hold is that the ANSWER is the same, which it is by construction: everything the
     * review asks the catalog is about an event's own day.
     */
    @Test
    void a_gig_years_away_from_the_calendar_is_neither_read_nor_needed() throws Exception {
        gigs.save(BAND, festGig());
        gigs.save(BAND, TestGigs.gig("Long ago", "Barrák",
                ZonedDateTime.of(FEST_DAY.minusYears(5).toLocalDate().atTime(20, 0), PRAGUE)));

        CalendarOverview overview = read(fest());

        assertThat(row(overview, "fest").match().state()).isEqualTo(CatalogMatch.State.SAME_DAY);
        assertThat(row(overview, "fest").match().gig().title()).isEqualTo("Fest");
    }

    /**
     * And the one thing narrowing the read could have broken: a link the band made itself, to a gig
     * that has since moved out of the window. Showing it as gone from the catalog would be the page
     * lying about the band's own decision, so the linked gigs are fetched by id whatever their day.
     */
    @Test
    void a_linked_gig_that_has_moved_out_of_the_window_is_still_linked() throws Exception {
        read(fest());
        catalog.addToCatalog(BAND, "fest", festGig());
        Gig moved = festGig().rescheduledTo(GigSchedule.startingAt(
                ZonedDateTime.of(FEST_DAY.plusYears(2).toLocalDate().atTime(20, 30), PRAGUE)));
        gigs.save(BAND, moved);

        CalendarOverview overview = read(fest());

        assertThat(row(overview, "fest").match().state()).isEqualTo(CatalogMatch.State.LINKED);
        assertThat(row(overview, "fest").match().gig().id()).isEqualTo(festGig().id());
    }

    @Test
    void an_event_is_not_linked_to_a_gig_that_is_gone() throws Exception {
        read(fest());

        assertThatThrownBy(() -> catalog.link(BAND, "fest", festGig().id())).isInstanceOf(NotFoundException.class);
        assertThat(links.all(BAND)).isEmpty();
    }

    @Test
    void a_linked_gig_shows_what_the_calendar_now_says_differently() throws Exception {
        read(fest(), event("club", "Club fest", FEST_DAY.plusDays(7), "Showtime: 21:00"));
        catalog.addToCatalog(BAND, "fest", festGig());
        Gig club = TestGigs.gig("Club fest", "Klub 007",
                ZonedDateTime.of(FEST_DAY.plusDays(7).toLocalDate().atTime(21, 0), PRAGUE));
        catalog.addToCatalog(BAND, "club", club);

        CalendarOverview overview = read(
                event("fest", "ZRUŠENÉ Fest", FEST_DAY.plusDays(1), "Showtime: 20:30"));

        assertThat(row(overview, "fest").match().differences()).extracting(CatalogMatch.Difference::kind)
                .containsExactly(CatalogMatch.Kind.CANCELLED, CatalogMatch.Kind.DATE);
        assertThat(row(overview, "club").match().differences()).extracting(CatalogMatch.Difference::kind)
                .containsExactly(CatalogMatch.Kind.REMOVED);

        service.seen(BAND, "club");

        assertThat(service.overview(BAND).rows()).extracting(CalendarRow::eventId).containsExactly("fest");
        assertThat(links.all(BAND)).doesNotContainKey("club");
    }

    @Test
    void gig_events_are_linked_to_the_one_catalog_gig_on_their_day() throws Exception {
        gigs.save(BAND, festGig());
        Gig twin1 = TestGigs.gig("A", "Klub A", ZonedDateTime.of(FEST_DAY.plusDays(1), PRAGUE));
        Gig twin2 = TestGigs.gig("B", "Klub B", ZonedDateTime.of(FEST_DAY.plusDays(1), PRAGUE));
        gigs.save(BAND, twin1);
        gigs.save(BAND, twin2);
        read(fest(), event("double", "Double fest", FEST_DAY.plusDays(1), "Showtime: 20:00"));

        assertThat(catalog.linkSameDayGigs(BAND)).isEqualTo(1);

        assertThat(links.all(BAND)).containsOnlyKeys("fest");
        assertThat(row(service.overview(BAND), "double").match().sameDay()).hasSize(2);
    }

    @Test
    void a_verdict_is_gig_or_not_a_gig() {
        service.decide(BAND, "uid", CalendarEventKind.GIG);
        service.forget(BAND, "other");

        assertThat(decisions.all(BAND)).containsOnlyKeys("uid");
        assertThatThrownBy(() -> service.decide(BAND, "uid", CalendarEventKind.UNSURE))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void each_band_has_its_own_calendar_copy_verdicts_and_links() throws Exception {
        read(fest());
        catalog.addToCatalog(BAND, "fest", festGig());
        service.decide(BAND, "fest", CalendarEventKind.GIG);

        assertThat(service.overview(OTHER).rows()).as("the other band read nothing").isEmpty();
        assertThat(service.overview(OTHER).lastRead()).isNull();
        assertThat(links.all(OTHER)).isEmpty();
        assertThat(decisions.all(OTHER)).isEmpty();
        assertThatThrownBy(() -> catalog.link(OTHER, "fest", festGig().id()))
                .as("the gig is the first band's").isInstanceOf(NotFoundException.class);
        assertThat(gigs.findAll(OTHER)).isEmpty();
    }
}
