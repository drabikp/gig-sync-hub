package sk.drabikp.bzscraper.calendar.domain;

import org.jmolecules.ddd.annotation.ValueObject;
import sk.drabikp.bzscraper.calendar.domain.CalendarChange.Field;
import sk.drabikp.bzscraper.calendar.domain.CalendarChange.Type;
import sk.drabikp.bzscraper.calendar.domain.event.CalendarEvent;
import sk.drabikp.bzscraper.calendar.domain.rules.CalendarClassification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pure domain service: compares a fresh read of the calendar with the saved copy. An event
 * not seen before is NEW, one that differs is CHANGED (with what changed, and what the rules
 * said before when the change made them say something else), one that is gone is REMOVED
 * and one that came back RETURNED. Changes the user hasn't seen add up. The very first read
 * is the starting point: nothing on it is "new".
 */
@ValueObject
public final class CalendarChanges {

    private CalendarChanges() {
    }

    /** The saved copy after this read: every event read now, plus the ones that just disappeared. */
    public static List<KnownCalendarEvent> afterRead(Map<String, KnownCalendarEvent> known,
                                                     List<CalendarClassification> read, Instant now) {
        boolean firstRead = known.isEmpty();
        List<KnownCalendarEvent> result = new ArrayList<>();
        Set<String> present = new HashSet<>();
        for (CalendarClassification c : read) {
            CalendarEvent event = c.event();
            present.add(event.id());
            KnownCalendarEvent before = known.get(event.id());
            CalendarChange change;
            if (before == null) {
                change = firstRead ? null : new CalendarChange(Type.NEW, Set.of(), null, now);
            } else if (before.removed()) {
                change = add(before.change(), new CalendarChange(Type.RETURNED, Set.of(), null, now));
            } else {
                Set<Field> fields = compare(before.event(), event);
                change = fields.isEmpty() ? before.change() : add(before.change(), new CalendarChange(Type.CHANGED,
                        fields, before.suggested() != c.suggested() ? before.suggested() : null, now));
            }
            result.add(new KnownCalendarEvent(event, c.suggested(), before == null ? now : before.firstSeen(), null,
                    change));
        }
        for (KnownCalendarEvent gone : known.values()) {
            if (!present.contains(gone.id()) && !gone.removed()) {
                result.add(new KnownCalendarEvent(gone.event(), gone.suggested(), gone.firstSeen(), now,
                        add(gone.change(), new CalendarChange(Type.REMOVED, Set.of(), null, now))));
            }
        }
        return result;
    }

    /** What differs between two reads of one event. */
    public static Set<Field> compare(CalendarEvent before, CalendarEvent after) {
        Set<Field> fields = EnumSet.noneOf(Field.class);
        if (!before.title().equals(after.title())) {
            fields.add(Field.TITLE);
        }
        if (!before.start().equals(after.start()) || !before.end().equals(after.end())
                || before.allDay() != after.allDay()) {
            fields.add(Field.TIME);
        }
        if (!before.location().equals(after.location())) {
            fields.add(Field.PLACE);
        }
        if (!before.notes().equals(after.notes())) {
            fields.add(Field.NOTES);
        }
        if (!Objects.equals(before.calendarStatus(), after.calendarStatus())) {
            fields.add(Field.STATUS);
        }
        return fields;
    }

    private static CalendarChange add(CalendarChange unseen, CalendarChange next) {
        return unseen == null ? next : unseen.then(next);
    }
}
