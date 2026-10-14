package sk.drabikp.gigsynchub.calendar.application.port.in;

import org.jmolecules.architecture.hexagonal.PrimaryPort;

import java.time.Instant;

/**
 * Letting go of the saved copy of events a band's calendar no longer has. The copy keeps every event
 * ever read, notes included — and the notes are where fees and phone numbers live — so an event gone
 * for months whose going has been seen is a row with somebody's details in it and nothing left to
 * decide about.
 */
@PrimaryPort
public interface ForgetOldCalendarCopyUseCase {

    /** Forgets events removed from the calendar before {@code when}; answers how many went. */
    int forgetRemovedBefore(Instant when);
}
