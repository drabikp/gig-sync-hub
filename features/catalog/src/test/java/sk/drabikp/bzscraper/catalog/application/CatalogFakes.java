package sk.drabikp.bzscraper.catalog.application;

import sk.drabikp.bzscraper.catalog.application.port.out.GigMovedListener;
import sk.drabikp.bzscraper.gig.application.port.out.LiveUpdates;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.sync.application.SyncFakes;
import sk.drabikp.bzscraper.sync.application.SyncRequests;

import java.util.ArrayList;
import java.util.List;

/** The catalog's write path and use cases over {@link SyncFakes}, for the tests of the features that write gigs. */
public final class CatalogFakes {

    private CatalogFakes() {
    }

    /** Records the identity moves it is told about. */
    public static final class Moves implements GigMovedListener {

        public final List<String> moved = new ArrayList<>();

        @Override
        public void gigMoved(BandId band, GigId from, GigId to) {
            moved.add(from + " -> " + to);
        }
    }

    public static CatalogWrites writes(SyncFakes.Gigs gigs, SyncFakes.Published published, SyncFakes.Outbox outbox,
                                       SyncRequests requests, GigMovedListener... listeners) {
        return new CatalogWrites(gigs, published, List.of(listeners), requests, SyncFakes.state(outbox));
    }

    public static GigCatalogService catalog(SyncFakes.Gigs gigs, SyncFakes.Published published,
                                            SyncFakes.Outbox outbox, SyncRequests requests,
                                            GigMovedListener... listeners) {
        return catalog(gigs, published, new SyncFakes.DirectTransactions(), outbox, requests, listeners);
    }

    public static GigCatalogService catalog(SyncFakes.Gigs gigs, SyncFakes.Published published,
                                            SyncFakes.DirectTransactions transactions, SyncFakes.Outbox outbox,
                                            SyncRequests requests, GigMovedListener... listeners) {
        return new GigCatalogService(gigs, published, transactions, requests,
                writes(gigs, published, outbox, requests, listeners), LiveUpdates.NONE);
    }
}
