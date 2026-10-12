package sk.drabikp.gigsynchub.calendar.adapter.out.ics;

import org.junit.jupiter.api.Test;
import sk.drabikp.gigsynchub.calendar.domain.event.CalendarEvent;
import sk.drabikp.gigsynchub.calendar.domain.event.CalendarEventStatus;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class IcsParserTest {

    /** Shaped like a Google calendar's iCal export (CRLF line ends, folded lines). */
    private static final String ICS = String.join("\r\n",
            "BEGIN:VCALENDAR",
            "PRODID:-//Google Inc//Google Calendar 70.9054//EN",
            "X-WR-CALNAME:BAND",
            "X-WR-TIMEZONE:Europe/Prague",
            "BEGIN:VEVENT",
            "DTSTART:20250308T160000Z",
            "DTEND:20250308T210000Z",
            "UID:gig1@google.com",
            "SUMMARY:Traktor Tour Pardubice",
            "LOCATION:ČEZ Aréna\\, Sukova tř. 1735\\, 530 02 Pardubice",
            "DESCRIPTION:<span>Soundcheck: 17:00<br>Doors: 18:00&nbsp\\;</span><br>Showtim",
            " e: 19:15 - 20:00!",
            "TRANSP:TRANSPARENT",
            "STATUS:CONFIRMED",
            "LAST-MODIFIED:20250301T101500Z",
            "BEGIN:VALARM",
            "ACTION:DISPLAY",
            "DESCRIPTION:This is an event reminder",
            "TRIGGER:-P0DT0H30M0S",
            "END:VALARM",
            "END:VEVENT",
            "BEGIN:VEVENT",
            "DTSTART;VALUE=DATE:20250713",
            "DTEND;VALUE=DATE:20250721",
            "UID:holiday@google.com",
            "SUMMARY:Jana dovolenka",
            "TRANSP:TRANSPARENT",
            "END:VEVENT",
            "BEGIN:VEVENT",
            "DTSTART;TZID=Europe/Prague:20240107T140000",
            "DTEND;TZID=Europe/Prague:20240107T150000",
            "RRULE:FREQ=WEEKLY;BYDAY=SU",
            "UID:call@google.com",
            "SUMMARY:Call porada",
            "END:VEVENT",
            "BEGIN:VEVENT",
            "DTSTART;TZID=Europe/Prague:20240128T140000",
            "RECURRENCE-ID;TZID=Europe/Prague:20240128T140000",
            "UID:call@google.com",
            "SUMMARY:Call porada",
            "END:VEVENT",
            "BEGIN:VEVENT",
            "DTSTART:20250516T150000Z",
            "UID:summer@google.com",
            "SUMMARY:ZRUŠENÉ Traktor OpenAir Plzeň",
            "DESCRIPTION:Koncert zrušený.\\nSpanie\\: hotel\\; raňajky\\, parkovanie",
            "STATUS:CANCELLED",
            "X-APPLE-STRUCTURED-LOCATION;VALUE=URI;X-TITLE=\"Place: with a colon\":geo:49.7,13.3",
            "END:VEVENT",
            "BEGIN:VEVENT",
            "SUMMARY:no uid — left out",
            "DTSTART:20250516T150000Z",
            "END:VEVENT",
            "END:VCALENDAR",
            "");

    private final Map<String, CalendarEvent> events = IcsParser.parse(ICS, ZoneId.of("UTC")).stream()
            .collect(Collectors.toMap(CalendarEvent::id, Function.identity()));

    @Test
    void reads_every_event_with_a_uid_and_ignores_reminders_inside_events() {
        assertThat(events.keySet()).containsExactlyInAnyOrder("gig1@google.com", "holiday@google.com",
                "call@google.com", "call@google.com/20240128T140000", "summer@google.com");
    }

    @Test
    void utc_times_are_converted_to_the_calendars_zone_including_summer_time() {
        CalendarEvent gig = events.get("gig1@google.com");
        assertThat(gig.start()).isEqualTo(LocalDateTime.of(2025, 3, 8, 17, 0));
        assertThat(gig.end()).isEqualTo(LocalDateTime.of(2025, 3, 8, 22, 0));
        assertThat(events.get("summer@google.com").start()).isEqualTo(LocalDateTime.of(2025, 5, 16, 17, 0));
    }

    @Test
    void html_notes_become_plain_lines_and_escapes_are_undone() {
        CalendarEvent gig = events.get("gig1@google.com");

        assertThat(gig.location()).isEqualTo("ČEZ Aréna, Sukova tř. 1735, 530 02 Pardubice");
        assertThat(gig.notes()).isEqualTo("Soundcheck: 17:00\nDoors: 18:00\nShowtime: 19:15 - 20:00!");
        assertThat(events.get("summer@google.com").notes())
                .isEqualTo("Koncert zrušený.\nSpanie: hotel; raňajky, parkovanie");
    }

    @Test
    void reads_free_time_status_and_last_change() {
        CalendarEvent gig = events.get("gig1@google.com");
        assertThat(gig.shownAsFree()).isTrue();
        assertThat(gig.calendarStatus()).isEqualTo(CalendarEventStatus.CONFIRMED);
        assertThat(gig.lastModified()).isEqualTo(Instant.parse("2025-03-01T10:15:00Z"));

        CalendarEvent summer = events.get("summer@google.com");
        assertThat(summer.calendarStatus()).isEqualTo(CalendarEventStatus.CANCELLED);
        assertThat(summer.end()).isEqualTo(summer.start());              // no DTEND
    }

    @Test
    void all_day_events_run_from_midnight_to_midnight_after_the_last_day() {
        CalendarEvent holiday = events.get("holiday@google.com");

        assertThat(holiday.allDay()).isTrue();
        assertThat(holiday.start()).isEqualTo(LocalDateTime.of(2025, 7, 13, 0, 0));
        assertThat(holiday.end()).isEqualTo(LocalDateTime.of(2025, 7, 21, 0, 0));
    }

    @Test
    void a_series_and_its_changed_occurrence_are_both_repeating_with_distinct_ids() {
        assertThat(events.get("call@google.com").repeating()).isTrue();
        assertThat(events.get("call@google.com").start()).isEqualTo(LocalDateTime.of(2024, 1, 7, 14, 0));
        assertThat(events.get("call@google.com/20240128T140000").repeating()).isTrue();
    }

    @Test
    void the_fallback_zone_is_used_when_the_calendar_names_none() {
        String noZone = ICS.replace("X-WR-TIMEZONE:Europe/Prague\r\n", "");

        List<CalendarEvent> parsed = IcsParser.parse(noZone, ZoneId.of("Europe/Bratislava"));

        assertThat(parsed).filteredOn(e -> e.id().equals("gig1@google.com")).singleElement()
                .satisfies(e -> assertThat(e.start()).isEqualTo(LocalDateTime.of(2025, 3, 8, 17, 0)));
    }
}
