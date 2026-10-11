package sk.drabikp.bzscraper.calendar.domain.event;

import org.jmolecules.ddd.annotation.ValueObject;

/** What a calendar event is, for the band's gig admin. */
@ValueObject
public enum CalendarEventKind {
    GIG,
    /** The rules can't tell — the user decides. Never a remembered verdict. */
    UNSURE,
    NOT_GIG
}
