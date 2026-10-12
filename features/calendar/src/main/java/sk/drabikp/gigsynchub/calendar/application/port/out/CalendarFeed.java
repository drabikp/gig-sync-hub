package sk.drabikp.gigsynchub.calendar.application.port.out;

import sk.drabikp.gigsynchub.calendar.domain.event.CalendarEvent;
import sk.drabikp.gigsynchub.gig.domain.BandId;

import java.util.List;

/** The band's calendar, read-only. */
public interface CalendarFeed {

    /** False when no calendar address is configured. */
    boolean configured(BandId band);

    /** Every event in the calendar, past and future. */
    List<CalendarEvent> read(BandId band) throws CalendarUnavailableException;
}
