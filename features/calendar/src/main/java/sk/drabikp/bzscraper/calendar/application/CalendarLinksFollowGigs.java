package sk.drabikp.bzscraper.calendar.application;

import sk.drabikp.bzscraper.calendar.application.port.out.CalendarLinkStore;
import sk.drabikp.bzscraper.catalog.application.port.out.GigMovedListener;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.GigId;

/** A calendar event stays linked to its gig when an edit moves the gig's identity. */
public class CalendarLinksFollowGigs implements GigMovedListener {

    private final CalendarLinkStore links;

    public CalendarLinksFollowGigs(CalendarLinkStore links) {
        this.links = links;
    }

    @Override
    public void gigMoved(BandId band, GigId from, GigId to) {
        links.move(band, from, to);
    }
}
