package sk.drabikp.gigsynchub.gig.domain;

import org.jmolecules.ddd.annotation.ValueObject;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Value object: when a gig happens. {@code start}/{@code end} are the whole EVENT (a
 * festival may run for days); {@code slot} is optionally when the band itself plays, and
 * lies within the event. A start is required; an end is optional but, when present, must
 * not be before the start. Immutable and self-validating — an invalid schedule cannot exist.
 */
@ValueObject
public record GigSchedule(ZonedDateTime start, ZonedDateTime end, Slot slot) {

    /** How many days of a long event count as "the gig's days" when matching by day. */
    private static final int MAX_DAYS = 14;

    public GigSchedule {
        if (start == null) {
            throw new IllegalArgumentException("gig start is required");
        }
        if (end != null && end.isBefore(start)) {
            throw new IllegalArgumentException("gig end must not be before start");
        }
        if (slot != null && (slot.start().isBefore(start) || end != null && slot.start().isAfter(end))) {
            throw new IllegalArgumentException("the band's slot must lie within the event");
        }
    }

    public GigSchedule(ZonedDateTime start, ZonedDateTime end) {
        this(start, end, null);
    }

    public static GigSchedule startingAt(ZonedDateTime start) {
        return new GigSchedule(start, null, null);
    }

    public GigSchedule withSlot(Slot newSlot) {
        return new GigSchedule(start, end, newSlot);
    }

    public LocalDate startDate() {
        return start.toLocalDate();
    }

    public boolean hasEnd() {
        return end != null;
    }

    public boolean hasSlot() {
        return slot != null;
    }

    /** When the band plays: its slot, or the event's start when no slot is given. */
    public ZonedDateTime showStart() {
        return slot != null ? slot.start() : start;
    }

    /** The end of the band's slot, or of the event when no slot is given (null when unknown). */
    public ZonedDateTime showEnd() {
        return slot != null ? slot.end() : end;
    }

    /** The event lasts into another day (ignoring an end at midnight right after the first day). */
    public boolean multiDay() {
        return end != null && lastDay().isAfter(startDate());
    }

    /** The event's days, first to last (at most two weeks of them). */
    public List<LocalDate> days() {
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate d = startDate(); !d.isAfter(lastDay()) && days.size() < MAX_DAYS; d = d.plusDays(1)) {
            days.add(d);
        }
        return days;
    }

    /** The day is one of the event's days (an "event" longer than two weeks is bad data: its first two weeks). */
    public boolean covers(LocalDate day) {
        LocalDate last = lastDay();
        LocalDate cap = startDate().plusDays(MAX_DAYS - 1L);
        return !day.isBefore(startDate()) && !day.isAfter(last.isAfter(cap) ? cap : last);
    }

    private LocalDate lastDay() {
        if (end == null) {
            return startDate();
        }
        LocalDate last = end.toLocalDate();
        // an event "until 00:00" ends with the day before
        return end.toLocalTime().equals(LocalTime.MIDNIGHT) && last.isAfter(startDate())
                ? last.minusDays(1) : last;
    }
}
