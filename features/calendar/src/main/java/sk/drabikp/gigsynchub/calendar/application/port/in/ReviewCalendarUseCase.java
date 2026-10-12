package sk.drabikp.gigsynchub.calendar.application.port.in;

import sk.drabikp.gigsynchub.calendar.application.port.out.CalendarUnavailableException;
import sk.drabikp.gigsynchub.calendar.domain.CalendarOverview;
import sk.drabikp.gigsynchub.calendar.domain.event.CalendarEventKind;
import sk.drabikp.gigsynchub.calendar.domain.rules.ProfileRule;
import sk.drabikp.gigsynchub.gig.domain.BandId;

import java.util.List;

/**
 * Sorting a band calendar's events into gigs and everything else, with the band's
 * profile, remembering the user's answers, and keeping a copy of the calendar so each read
 * shows what is new, changed or gone. Never changes the calendar.
 */
public interface ReviewCalendarUseCase {

    boolean configured(BandId band);

    /** The calendar as last read, sorted and matched against the catalog — without reading it again. */
    CalendarOverview overview(BandId band);

    /** Reads the calendar, compares it with the last read and saves it; then as {@link #overview(BandId)}. */
    CalendarOverview read(BandId band) throws CalendarUnavailableException;

    /** Remembers the user's verdict — {@code GIG} or {@code NOT_GIG} — for the event. */
    void decide(BandId band, String eventId, CalendarEventKind verdict);

    /** Forgets the verdict; the profile decides again. */
    void forget(BandId band, String eventId);

    /** The user has seen the event's change; an event gone from the calendar is then forgotten (and unlinked). */
    void seen(BandId band, String eventId);

    void seenAll(BandId band);

    List<ProfileRule> rules(BandId band);
}
