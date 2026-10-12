package sk.drabikp.gigsynchub.calendar.domain;

import org.junit.jupiter.api.Test;
import sk.drabikp.gigsynchub.calendar.domain.CalendarGigDrafter.Place;
import sk.drabikp.gigsynchub.calendar.domain.event.CalendarEvent;
import sk.drabikp.gigsynchub.calendar.domain.event.CalendarEventStatus;
import sk.drabikp.gigsynchub.calendar.domain.rules.BandProfile;
import sk.drabikp.gigsynchub.calendar.domain.rules.ProfileRule;
import sk.drabikp.gigsynchub.calendar.domain.rules.RuleKind;
import sk.drabikp.gigsynchub.gig.domain.Country;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarGigDrafterTest {

    private static final BandProfile PROFILE = new BandProfile(List.of(
            ProfileRule.preset(RuleKind.SHOWTIME_LABEL, "showtime|show time|show", 0),
            ProfileRule.preset(RuleKind.SHOWTIME_LABEL, "cas predbezne", 0)),
            new BandProfile.Thresholds(4, -1, -4));

    private static CalendarEvent event(LocalDateTime start, boolean allDay, String location, String notes) {
        return new CalendarEvent("uid", "Autumn Fest 2026", location, notes, start, start.plusHours(8), allDay,
                false, false, CalendarEventStatus.CONFIRMED, null);
    }

    @Test
    void the_show_time_comes_from_the_notes_not_from_the_arrival() {
        CalendarGigDraft draft = CalendarGigDrafter.draft(event(LocalDateTime.of(2026, 11, 7, 15, 30), false, "",
                "Príchod: 16:30\nSoundcheck: 17:30\nDoors: 18:30\n- Showtime: 19:30\nCena: 500 €"), PROFILE);

        assertThat(draft.title()).isEqualTo("Autumn Fest 2026");
        assertThat(draft.date()).isEqualTo(LocalDate.of(2026, 11, 7));
        assertThat(draft.showTime()).isEqualTo(LocalTime.of(19, 30));
        assertThat(draft.eventStart()).isEqualTo(LocalTime.of(15, 30));
    }

    @Test
    void any_of_the_bands_show_labels_with_a_dot_or_colon_and_no_time_without_a_label() {
        assertThat(CalendarGigDrafter.showTime("show: 19.50", PROFILE)).contains(LocalTime.of(19, 50));
        assertThat(CalendarGigDrafter.showTime("Čas predbežne: 21:00", PROFILE)).contains(LocalTime.of(21, 0));
        assertThat(CalendarGigDrafter.showTime("Soundcheck: 17:00\nDoors: 18:00", PROFILE)).isEmpty();
        assertThat(CalendarGigDrafter.showTime("Showtime: tba", PROFILE)).isEmpty();
    }

    @Test
    void a_show_after_midnight_is_on_the_next_day() {
        CalendarGigDraft draft = CalendarGigDrafter.draft(event(LocalDateTime.of(2026, 8, 15, 21, 0), false, "",
                "Arrival: 23:00\nShowtime: 00:30"), PROFILE);

        assertThat(draft.date()).isEqualTo(LocalDate.of(2026, 8, 16));
    }

    @Test
    void venue_city_and_country_are_read_from_a_map_address() {
        assertThat(CalendarGigDrafter.place("Club Orbit, Hlavná 12, 811 05 Bratislava-Staré Mesto, Slovensko"))
                .isEqualTo(new Place("Club Orbit", "Bratislava", Country.SLOVAKIA, "Hlavná 12", "811 05"));
        assertThat(CalendarGigDrafter.place("Music Hall, Long Street 51, 186 00 Praha 8-Karlín, Česko"))
                .isEqualTo(new Place("Music Hall", "Praha", Country.CZECHIA, "Long Street 51", "186 00"));
        assertThat(CalendarGigDrafter.place("Town Hall, Main Sq. 1, 460 01 Liberec IV-Perštýn, Czechia"))
                .isEqualTo(new Place("Town Hall", "Liberec", Country.CZECHIA, "Main Sq. 1", "460 01"));
        assertThat(CalendarGigDrafter.place("Garden Bar, 3267, 508 01 Hořice-Hořice v Podkrkonoší, Česko"))
                .as("a bare number is no street").isEqualTo(new Place("Garden Bar", "Hořice", Country.CZECHIA, null, "508 01"));
        assertThat(CalendarGigDrafter.place("Secret Yard, Main St 36, 040 01 Staré Mesto, Slovensko"))
                .as("Košice addresses name only the district")
                .isEqualTo(new Place("Secret Yard", "Košice", Country.SLOVAKIA, "Main St 36", "040 01"));
    }

    @Test
    void a_place_that_is_only_a_town_has_no_venue() {
        assertThat(CalendarGigDrafter.place("Kraslice, 358 01 Kraslice, Česko"))
                .isEqualTo(new Place(null, "Kraslice", Country.CZECHIA, null, "358 01"));
        assertThat(CalendarGigDrafter.place("Práče\nOkres Znojmo, Česko"))
                .isEqualTo(new Place(null, "Práče", Country.CZECHIA));
        assertThat(CalendarGigDrafter.place("X766+59, Prešov, Slovensko"))
                .isEqualTo(new Place(null, "Prešov", Country.SLOVAKIA));
    }

    @Test
    void without_a_postal_code_the_place_is_venue_then_city() {
        assertThat(CalendarGigDrafter.place("Rock Cellar, Zlín")).isEqualTo(new Place("Rock Cellar", "Zlín", null));
        assertThat(CalendarGigDrafter.place("Frýdek-Místek")).isEqualTo(new Place(null, "Frýdek-Místek", null));
        assertThat(CalendarGigDrafter.place("Hall, Long St 9 466 01, 1 Jablonec nad Nisou 1, Česko"))
                .extracting(Place::venue, Place::city).containsExactly("Hall", "Jablonec nad Nisou");
    }

    @Test
    void coordinates_and_empty_places_say_nothing() {
        assertThat(CalendarGigDrafter.place("49.4711267, 17.1134370")).isEqualTo(Place.NOWHERE);
        assertThat(CalendarGigDrafter.place("48.9790119N, 14.4612478E")).isEqualTo(Place.NOWHERE);
        assertThat(CalendarGigDrafter.place("")).isEqualTo(Place.NOWHERE);
        assertThat(CalendarGigDrafter.place("https://maps.app.goo.gl/abc123")).isEqualTo(Place.NOWHERE);
    }

    @Test
    void an_all_day_event_has_no_start_time() {
        CalendarGigDraft draft = CalendarGigDrafter.draft(event(LocalDateTime.of(2026, 7, 18, 0, 0), true,
                "Hořice, 508 01 Hořice-Hořice v Podkrkonoší, Česko", ""), PROFILE);

        assertThat(draft.eventStart()).isNull();
        assertThat(draft.showTime()).isNull();
        assertThat(draft.city()).isEqualTo("Hořice");
        assertThat(draft.venue()).isNull();
    }
}
