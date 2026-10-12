package sk.drabikp.gigsynchub.calendar.domain.event;

import org.jmolecules.ddd.annotation.ValueObject;

/** Whether a calendar event (a gig) is going ahead. */
@ValueObject
public enum CalendarEventStatus {
    CONFIRMED,
    /** Not agreed yet ("PREDBEŽNE", "v jednaní") — may be linked, must not be published. */
    TENTATIVE,
    CANCELLED
}
