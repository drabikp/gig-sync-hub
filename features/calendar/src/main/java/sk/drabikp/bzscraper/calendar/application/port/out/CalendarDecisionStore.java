package sk.drabikp.bzscraper.calendar.application.port.out;

import org.jmolecules.ddd.annotation.Repository;
import sk.drabikp.bzscraper.calendar.domain.event.CalendarEventKind;
import sk.drabikp.bzscraper.gig.domain.BandId;

import java.util.Map;

/** The user's verdicts on calendar events (gig / not a gig), by event id, kept forever. */
@Repository
public interface CalendarDecisionStore {

    Map<String, CalendarEventKind> all(BandId band);

    void decide(BandId band, String eventId, CalendarEventKind verdict);

    void forget(BandId band, String eventId);
}
