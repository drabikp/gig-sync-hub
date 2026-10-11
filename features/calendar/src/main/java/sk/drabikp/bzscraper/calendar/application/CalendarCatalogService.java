package sk.drabikp.bzscraper.calendar.application;

import org.jmolecules.architecture.hexagonal.Application;
import sk.drabikp.bzscraper.calendar.application.port.in.CalendarCatalogUseCase;
import sk.drabikp.bzscraper.calendar.application.port.in.ReviewCalendarUseCase;
import sk.drabikp.bzscraper.calendar.application.port.out.CalendarLinkStore;
import sk.drabikp.bzscraper.calendar.domain.CalendarRow;
import sk.drabikp.bzscraper.catalog.application.port.in.GigWrites;
import sk.drabikp.bzscraper.catalog.application.port.in.ListGigsUseCase;
import sk.drabikp.bzscraper.gig.application.port.out.LiveUpdates;
import sk.drabikp.bzscraper.gig.application.port.out.Transactions;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.GigId;

import java.util.List;

/**
 * Brings the calendar's gigs into the catalog ({@link CalendarCatalogUseCase}): a gig added from
 * an event goes through the catalog's write path ({@link GigWrites}, so its rules hold), and the
 * event is linked to it; or an event is linked to the gig already there. Never writes the calendar.
 */
@Application
public class CalendarCatalogService implements CalendarCatalogUseCase {

    private final ReviewCalendarUseCase review;
    private final GigWrites writes;
    private final ListGigsUseCase gigs;
    private final CalendarLinkStore linkStore;
    private final Transactions transactions;
    private final LiveUpdates live;

    public CalendarCatalogService(ReviewCalendarUseCase review, GigWrites writes, ListGigsUseCase gigs,
                                  CalendarLinkStore linkStore, Transactions transactions, LiveUpdates live) {
        this.review = review;
        this.writes = writes;
        this.gigs = gigs;
        this.linkStore = linkStore;
        this.transactions = transactions;
        this.live = live;
    }

    @Override
    public void addToCatalog(BandId band, String eventId, Gig gig) {
        transactions.inTransaction(() -> {
            writes.add(band, gig);
            linkStore.link(band, eventId, gig.id());
        });
        live.changed(band, LiveUpdates.Topic.CALENDAR);
        live.changed(band, LiveUpdates.Topic.GIGS);
    }

    @Override
    public void link(BandId band, String eventId, GigId gigId) {
        transactions.inTransaction(() -> linkStore.link(band, eventId, gigs.gig(band, gigId).id()));
        live.changed(band, LiveUpdates.Topic.CALENDAR);
    }

    @Override
    public void unlink(BandId band, String eventId) {
        linkStore.unlink(band, eventId);
        live.changed(band, LiveUpdates.Topic.CALENDAR);
    }

    @Override
    public int linkSameDayGigs(BandId band) {
        List<CalendarRow> clear = review.overview(band).linkableRows();
        transactions.inTransaction(() -> clear.forEach(r -> linkStore.link(band, r.eventId(),
                r.match().gig().id())));
        live.changed(band, LiveUpdates.Topic.CALENDAR);
        return clear.size();
    }
}
