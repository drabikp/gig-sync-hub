package sk.drabikp.gigsynchub.calendar.domain;

import org.junit.jupiter.api.Test;
import sk.drabikp.gigsynchub.calendar.domain.event.CalendarEvent;
import sk.drabikp.gigsynchub.calendar.domain.event.CalendarEventKind;
import sk.drabikp.gigsynchub.calendar.domain.event.CalendarEventStatus;
import sk.drabikp.gigsynchub.calendar.domain.rules.CalendarClassification;
import sk.drabikp.gigsynchub.gig.domain.Country;
import sk.drabikp.gigsynchub.gig.domain.Gig;
import sk.drabikp.gigsynchub.gig.domain.TestGigs;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarFilterTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 8);

    private static CalendarRow row(LocalDate day, CalendarEventKind kind, CalendarEventStatus status,
                                   CatalogMatch.State state, CalendarChange change, boolean removed) {
        return row(day, kind, status, new CatalogMatch(state, null, List.of(), List.of()), change, removed);
    }

    private static CalendarRow row(LocalDate day, CalendarEventKind kind, CalendarEventStatus status,
                                   CatalogMatch match, CalendarChange change, boolean removed) {
        CatalogMatch.State state = match.state();
        CalendarEvent event = new CalendarEvent("e-" + day + kind + status + state + removed, "Event", "", "",
                day.atTime(18, 0), null, false, false, false, status, null);
        CalendarClassification classification = new CalendarClassification(event, kind, kind, false, status, 0,
                List.of());
        CalendarGigDraft draft = new CalendarGigDraft(event.id(), "Event", day, null, null, "Praha", Country.CZECHIA,
                null, null, null);
        return new CalendarRow(classification, draft, match, change, removed);
    }

    private static CalendarRow gig(LocalDate day, CatalogMatch.State state, Gig gig) {
        return row(day, CalendarEventKind.GIG, CalendarEventStatus.CONFIRMED,
                new CatalogMatch(state, gig, state == CatalogMatch.State.SAME_DAY ? List.of(gig) : List.of(), List.of()),
                null, false);
    }

    private static CalendarRow gig(LocalDate day, CatalogMatch.State state) {
        return row(day, CalendarEventKind.GIG, CalendarEventStatus.CONFIRMED, state, null, false);
    }

    @Test
    void needs_a_look_shows_recent_and_upcoming_gigs_missing_from_the_catalog_but_not_old_or_cancelled_ones() {
        CalendarRow upcoming = gig(TODAY.plusDays(10), CatalogMatch.State.MISSING);
        CalendarRow recent = gig(TODAY.minusDays(CalendarFilter.RECENT_DAYS), CatalogMatch.State.MISSING);
        CalendarRow old = gig(TODAY.minusDays(CalendarFilter.RECENT_DAYS + 1), CatalogMatch.State.MISSING);
        CalendarRow linked = gig(TODAY.plusDays(10), CatalogMatch.State.LINKED);
        CalendarRow cancelled = row(TODAY.plusDays(3), CalendarEventKind.GIG, CalendarEventStatus.CANCELLED,
                CatalogMatch.State.MISSING, null, false);

        assertThat(List.of(upcoming, recent, old, linked, cancelled).stream()
                .filter(CalendarFilter.NEEDS_A_LOOK.on(TODAY))).containsExactly(upcoming, recent);
        assertThat(List.of(upcoming, old, linked, cancelled).stream().filter(CalendarFilter.MISSING.on(TODAY)))
                .containsExactly(upcoming, old, cancelled);
    }

    @Test
    void needs_a_look_shows_unseen_changes_any_time_and_unsure_events_only_ahead() {
        CalendarRow changedLongAgo = row(TODAY.minusYears(1), CalendarEventKind.NOT_GIG, CalendarEventStatus.CONFIRMED,
                CatalogMatch.State.MISSING, new CalendarChange(CalendarChange.Type.CHANGED,
                        Set.of(CalendarChange.Field.TIME), null, Instant.parse("2026-10-01T10:00:00Z")), false);
        CalendarRow unsureAhead = row(TODAY, CalendarEventKind.UNSURE, CalendarEventStatus.CONFIRMED,
                CatalogMatch.State.MISSING, null, false);
        CalendarRow unsurePast = row(TODAY.minusDays(1), CalendarEventKind.UNSURE, CalendarEventStatus.CONFIRMED,
                CatalogMatch.State.MISSING, null, false);
        CalendarRow unsureRemoved = row(TODAY.plusDays(1), CalendarEventKind.UNSURE, CalendarEventStatus.CONFIRMED,
                CatalogMatch.State.MISSING, null, true);

        assertThat(List.of(changedLongAgo, unsureAhead, unsurePast, unsureRemoved).stream()
                .filter(CalendarFilter.NEEDS_A_LOOK.on(TODAY))).containsExactly(changedLongAgo, unsureAhead);
        assertThat(List.of(unsureAhead, unsurePast, unsureRemoved).stream().filter(CalendarFilter.NOT_SURE.on(TODAY)))
                .containsExactly(unsureAhead, unsurePast);
    }

    @Test
    void the_counts_leave_removed_events_out_and_count_only_recent_missing_gigs() {
        List<CalendarRow> rows = List.of(
                gig(TODAY.plusDays(10), CatalogMatch.State.MISSING),
                gig(TODAY.minusYears(1), CatalogMatch.State.MISSING),
                gig(TODAY.plusDays(20), CatalogMatch.State.LINKED),
                row(TODAY, CalendarEventKind.UNSURE, CalendarEventStatus.CONFIRMED, CatalogMatch.State.MISSING, null,
                        false),
                row(TODAY, CalendarEventKind.GIG, CalendarEventStatus.CONFIRMED, CatalogMatch.State.MISSING, null,
                        true));

        CalendarFilter.Counts counts = CalendarFilter.count(rows, TODAY);

        assertThat(counts.events()).isEqualTo(4);
        assertThat(counts.gigs()).isEqualTo(3);
        assertThat(counts.notSure()).isEqualTo(1);
        assertThat(counts.missing()).isEqualTo(1);
        assertThat(counts.linkable()).isZero();
    }

    @Test
    void only_events_the_bulk_link_takes_are_counted_as_linkable() {
        Gig fest = TestGigs.gig("Fest", "Klub 007");
        Gig club = TestGigs.gig("Club", "Klub 008");
        Gig bar = TestGigs.gig("Bar", "Klub 009");
        CalendarRow festFirstDay = gig(TODAY.plusDays(1), CatalogMatch.State.LINKED, fest);
        CalendarRow festSecondDay = gig(TODAY.plusDays(2), CatalogMatch.State.SAME_DAY, fest);
        CalendarRow clubArrival = gig(TODAY.plusDays(3), CatalogMatch.State.SAME_DAY, club);
        CalendarRow clubShow = gig(TODAY.plusDays(4), CatalogMatch.State.SAME_DAY, club);
        CalendarRow barShow = gig(TODAY.plusDays(5), CatalogMatch.State.SAME_DAY, bar);
        List<CalendarRow> rows = List.of(festFirstDay, festSecondDay, clubArrival, clubShow, barShow);

        assertThat(CalendarFilter.linkable(rows)).containsExactly(barShow);
        assertThat(CalendarFilter.count(rows, TODAY).linkable()).isEqualTo(1);
    }
}
