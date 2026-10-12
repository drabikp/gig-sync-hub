package sk.drabikp.gigsynchub.calendar.application.port.in;

import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.gig.domain.Gig;
import sk.drabikp.gigsynchub.gig.domain.GigId;

/**
 * Bringing the calendar's gigs into the catalog: add a gig from an event (the user checks
 * the pre-filled form), or link an event to the gig the catalog already has. A linked
 * event is how the calendar page tells the user when the calendar changes the gig.
 * Publishing stays a separate step in the catalog.
 */
public interface CalendarCatalogUseCase {

    /**
     * Saves {@code gig} to the catalog, linked to the event.
     *
     * @throws sk.drabikp.gigsynchub.catalog.application.port.in.GigIdentityTakenException if the catalog
     *         already has a gig with that identity (link the event to it instead)
     */
    void addToCatalog(BandId band, String eventId, Gig gig);

    /**
     * Links the event to the catalog's gig.
     *
     * @throws sk.drabikp.gigsynchub.gig.application.NotFoundException if the gig is no longer in the catalog
     */
    void link(BandId band, String eventId, GigId gigId);

    void unlink(BandId band, String eventId);

    /** Links every gig event whose day has exactly one catalog gig not linked to another event; returns how many. */
    int linkSameDayGigs(BandId band);
}
