package sk.drabikp.gigsynchub.calendar.domain;

import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;
import sk.drabikp.gigsynchub.calendar.domain.event.CalendarEvent;
import sk.drabikp.gigsynchub.calendar.domain.event.CalendarEventKind;

import java.time.Instant;

/**
 * A calendar event as the app last read it — the saved copy that the next read is
 * compared with. {@code suggested} is what the rules said then; {@code removedAt} is set
 * once the event is gone from the calendar; {@code change} is what the user hasn't seen
 * yet (null when nothing).
 */
@AggregateRoot
public record KnownCalendarEvent(@Identity CalendarEvent event, CalendarEventKind suggested, Instant firstSeen,
                                 Instant removedAt, CalendarChange change) {

    public KnownCalendarEvent {
        if (event == null || suggested == null || firstSeen == null) {
            throw new IllegalArgumentException("event, suggestion and first seen are required");
        }
    }

    public String id() {
        return event.id();
    }

    public boolean removed() {
        return removedAt != null;
    }

    public KnownCalendarEvent seen() {
        return new KnownCalendarEvent(event, suggested, firstSeen, removedAt, null);
    }
}
