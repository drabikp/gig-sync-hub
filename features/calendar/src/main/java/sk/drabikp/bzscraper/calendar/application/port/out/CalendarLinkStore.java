package sk.drabikp.bzscraper.calendar.application.port.out;

import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.GigId;

import java.util.Map;

/**
 * Which catalog gig each calendar event is, by event id. A link may point at a gig that
 * was deleted since; it then matches nothing.
 */
public interface CalendarLinkStore {

    Map<String, GigId> all(BandId band);

    void link(BandId band, String eventId, GigId gigId);

    void unlink(BandId band, String eventId);

    /** Re-keys the links of a gig whose identity changed (an edit moved its date or venue). */
    void move(BandId band, GigId from, GigId to);
}
