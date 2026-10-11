package sk.drabikp.bzscraper.calendar.domain;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.calendar.domain.event.CalendarEventStatus;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.gig.domain.GigSchedule;
import sk.drabikp.bzscraper.gig.domain.Slot;
import sk.drabikp.bzscraper.gig.domain.TestGigs;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarCatalogMatcherTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 8);
    private static final Gig GIG = TestGigs.gig("Fest", "Klub",
            ZonedDateTime.of(2026, 11, 20, 20, 0, 0, 0, ZoneId.of("Europe/Prague")));

    private static CalendarGigDraft draft(LocalDate date, LocalTime showTime) {
        return new CalendarGigDraft("uid", "Fest", date, showTime, "Klub", "Praha", null, null, null,
                LocalTime.of(16, 0));
    }

    private static CatalogMatch match(CalendarGigDraft draft, CalendarEventStatus status, boolean removed, GigId link) {
        return CalendarCatalogMatcher.match(draft, status, removed, link, Map.of(GIG.id(), GIG), TODAY);
    }

    @Test
    void a_linked_gig_agreeing_with_the_calendar_has_no_differences() {
        CatalogMatch match = match(draft(GIG.schedule().startDate(), LocalTime.of(20, 0)),
                CalendarEventStatus.CONFIRMED, false, GIG.id());

        assertThat(match.state()).isEqualTo(CatalogMatch.State.LINKED);
        assertThat(match.differences()).isEmpty();
    }

    @Test
    void another_show_time_or_a_cancelled_or_removed_event_is_a_difference() {
        assertThat(match(draft(GIG.schedule().startDate(), LocalTime.of(21, 0)), CalendarEventStatus.CONFIRMED,
                false, GIG.id()).has(CatalogMatch.Kind.SHOW_TIME)).isTrue();
        assertThat(match(draft(GIG.schedule().startDate(), null), CalendarEventStatus.CANCELLED,
                false, GIG.id()).differences()).extracting(CatalogMatch.Difference::kind)
                .containsExactly(CatalogMatch.Kind.CANCELLED);
        assertThat(match(draft(LocalDate.of(2026, 11, 21), null), CalendarEventStatus.CONFIRMED,
                true, GIG.id()).differences()).extracting(CatalogMatch.Difference::kind)
                .containsExactly(CatalogMatch.Kind.REMOVED);
    }

    @Test
    void past_gigs_are_not_compared() {
        Gig past = TestGigs.gig("Old", "Klub", ZonedDateTime.of(2026, 5, 1, 20, 0, 0, 0, ZoneId.of("Europe/Prague")));

        CatalogMatch match = CalendarCatalogMatcher.match(draft(LocalDate.of(2026, 5, 2), null),
                CalendarEventStatus.CANCELLED, true, past.id(), Map.of(past.id(), past), TODAY);

        assertThat(match.state()).isEqualTo(CatalogMatch.State.LINKED);
        assertThat(match.differences()).isEmpty();
    }

    @Test
    void an_unlinked_event_or_one_linked_to_a_deleted_gig_is_matched_by_day() {
        GigId deleted = TestGigs.id("a gig that is gone");

        assertThat(match(draft(GIG.schedule().startDate(), null), CalendarEventStatus.CONFIRMED, false, deleted))
                .satisfies(m -> {
                    assertThat(m.state()).isEqualTo(CatalogMatch.State.SAME_DAY);
                    assertThat(m.gig()).isEqualTo(GIG);
                });
        assertThat(match(draft(LocalDate.of(2026, 12, 1), null), CalendarEventStatus.CONFIRMED, false, null).state())
                .isEqualTo(CatalogMatch.State.MISSING);
    }

    @Test
    void a_festival_pairs_on_any_of_its_days_and_the_calendars_show_is_offered_as_the_bands_slot() {
        ZoneId zone = ZoneId.of("Europe/Bratislava");
        Gig festival = new Gig(GigId.fresh(), "Moto Fest", new GigSchedule(ZonedDateTime.of(2026, 11, 27, 0, 0, 0, 0, zone),
                ZonedDateTime.of(2026, 11, 30, 0, 0, 0, 0, zone)), GIG.location(), List.of(), GIG.admission(),
                null, null, null, null, false);
        CalendarGigDraft show = draft(LocalDate.of(2026, 11, 28), LocalTime.of(19, 30));

        CatalogMatch unlinked = CalendarCatalogMatcher.match(show, CalendarEventStatus.CONFIRMED, false, null,
                Map.of(festival.id(), festival), TODAY);
        CatalogMatch linked = CalendarCatalogMatcher.match(show, CalendarEventStatus.CONFIRMED, false, festival.id(),
                Map.of(festival.id(), festival), TODAY);
        Gig withSlot = new Gig(festival.id(), "Moto Fest", festival.schedule().withSlot(new Slot(
                ZonedDateTime.of(2026, 11, 28, 19, 30, 0, 0, zone), null)), GIG.location(), List.of(),
                GIG.admission(), null, null, null, null, false);
        CatalogMatch agreeing = CalendarCatalogMatcher.match(show, CalendarEventStatus.CONFIRMED, false, withSlot.id(),
                Map.of(withSlot.id(), withSlot), TODAY);

        assertThat(unlinked.state()).isEqualTo(CatalogMatch.State.SAME_DAY);
        assertThat(unlinked.gig()).isEqualTo(festival);
        assertThat(linked.differences()).extracting(CatalogMatch.Difference::kind)
                .containsExactly(CatalogMatch.Kind.SHOW_TIME);
        assertThat(linked.differences().getFirst().text()).contains("band's slot");
        assertThat(agreeing.differences()).isEmpty();
    }
}
