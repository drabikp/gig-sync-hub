package sk.drabikp.gigsynchub.calendar.application.port.out;

import org.jmolecules.ddd.annotation.Repository;
import sk.drabikp.gigsynchub.calendar.domain.KnownCalendarEvent;
import sk.drabikp.gigsynchub.gig.domain.BandId;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * The calendar as the app last read it ({@link KnownCalendarEvent}s by event id): what the
 * next read is compared with, and what the calendar page shows without reading again.
 * Holds the band's private notes — never published.
 */
@Repository
public interface CalendarSnapshotStore {

    Map<String, KnownCalendarEvent> all(BandId band);

    Optional<KnownCalendarEvent> find(BandId band, String eventId);

    /** Stores the copy after a read made at {@code readAt}; events not in {@code events} are left as they are. */
    void saveRead(BandId band, Collection<KnownCalendarEvent> events, Instant readAt);

    /** Stores one event's new state without counting as a read (e.g. a change marked seen). */
    void save(BandId band, KnownCalendarEvent event);

    void remove(BandId band, String eventId);

    /** When the calendar was last read, if ever. */
    Optional<Instant> lastRead(BandId band);

    /**
     * Forgets events the calendar no longer has and whose going has been seen, removed before
     * {@code when}; answers how many went. The saved copy keeps every event a band's calendar ever
     * held, notes included — and the notes are where fees and phone numbers live, so an event gone
     * for half a year is a row with somebody's details in it and nothing left to decide.
     */
    int forgetRemovedBefore(Instant when);
}
