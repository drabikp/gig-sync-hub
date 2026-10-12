package sk.drabikp.gigsynchub.calendar.domain;

import org.jmolecules.ddd.annotation.ValueObject;
import sk.drabikp.gigsynchub.calendar.domain.event.CalendarEventKind;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/**
 * What happened to a calendar event between two reads, kept until the user has seen it.
 * {@code fields} says what changed ({@link Type#CHANGED} only); {@code suggestedBefore} is
 * what the rules said before the change, when the change made them say something else
 * ("was not sure, now a gig"), else null.
 */
@ValueObject
public record CalendarChange(Type type, Set<Field> fields, CalendarEventKind suggestedBefore, Instant at) {

    public enum Type { NEW, CHANGED, REMOVED, RETURNED }

    public enum Field { TITLE, TIME, PLACE, NOTES, STATUS }

    public CalendarChange {
        if (type == null || at == null) {
            throw new IllegalArgumentException("change type and time are required");
        }
        fields = fields == null || fields.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(fields));
    }

    /**
     * This change followed by {@code later}, both unseen: a new or returned event stays
     * that; changes add up, keeping what the rules said first.
     */
    public CalendarChange then(CalendarChange later) {
        if (later.type() != Type.CHANGED || type == Type.REMOVED) {
            return later;
        }
        if (type != Type.CHANGED) {
            return this;
        }
        Set<Field> all = EnumSet.noneOf(Field.class);
        all.addAll(fields);
        all.addAll(later.fields());
        return new CalendarChange(Type.CHANGED, all, suggestedBefore != null ? suggestedBefore : later.suggestedBefore(),
                later.at());
    }
}
