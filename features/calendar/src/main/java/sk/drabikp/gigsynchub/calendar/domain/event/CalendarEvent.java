package sk.drabikp.gigsynchub.calendar.domain.event;

import org.jmolecules.ddd.annotation.ValueObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One event from the band's calendar, as the calendar has it — nothing interpreted yet.
 * {@code id} is stable across reads (the calendar's uid; one occurrence of a repeating
 * event adds its original start). Times are in the band's time zone; an all-day event
 * runs from midnight of its first day to midnight after its last ({@code end}
 * exclusive). {@code calendarStatus} is the calendar's own status field — most calendars
 * leave every event "confirmed" and say cancelled/tentative in the title instead.
 */
@ValueObject
public record CalendarEvent(String id, String title, String location, String notes,
                            LocalDateTime start, LocalDateTime end, boolean allDay,
                            boolean repeating, boolean shownAsFree,
                            CalendarEventStatus calendarStatus, Instant lastModified) {

    public CalendarEvent {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("calendar event id is required");
        }
        if (start == null) {
            throw new IllegalArgumentException("calendar event start is required");
        }
        title = title == null ? "" : title.strip();
        location = location == null ? "" : location.strip();
        notes = notes == null ? "" : notes.strip();
        end = end == null || end.isBefore(start) ? start : end;
        calendarStatus = calendarStatus == null ? CalendarEventStatus.CONFIRMED : calendarStatus;
    }

    public LocalDate day() {
        return start.toLocalDate();
    }
}
