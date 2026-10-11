package sk.drabikp.bzscraper.calendar.application.port.out;

import org.jmolecules.ddd.annotation.Repository;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.GigId;

import java.util.Map;

/**
 * Which catalog gig each calendar event is, by event id. A link names the gig's identity, which it
 * keeps however it is edited, so a link never has to follow a gig anywhere. It may point at a gig
 * that was deleted since; it then matches nothing.
 */
@Repository
public interface CalendarLinkStore {

    Map<String, GigId> all(BandId band);

    void link(BandId band, String eventId, GigId gigId);

    void unlink(BandId band, String eventId);
}
