package sk.drabikp.gigsynchub.catalog.application;

import sk.drabikp.gigsynchub.gig.application.port.out.LiveUpdates;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import sk.drabikp.gigsynchub.sync.application.SyncFakes;
import sk.drabikp.gigsynchub.sync.application.SyncRequests;

/** The catalog's write path and use cases over {@link SyncFakes}, for the tests of the features that write gigs. */
public final class CatalogFakes {

    /** The day the fakes' catalog lives on, so a list's "upcoming" is the same in every test. */
    public static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-08T10:00:00Z"), ZoneId.of("Europe/Prague"));

    private CatalogFakes() {
    }

    public static CatalogWrites writes(SyncFakes.Gigs gigs, SyncFakes.Published published, SyncFakes.Outbox outbox,
                                       SyncRequests requests) {
        return new CatalogWrites(gigs, published, requests, SyncFakes.state(outbox));
    }

    public static GigCatalogService catalog(SyncFakes.Gigs gigs, SyncFakes.Published published,
                                            SyncFakes.Outbox outbox, SyncRequests requests) {
        return catalog(gigs, published, new SyncFakes.DirectTransactions(), outbox, requests);
    }

    public static GigCatalogService catalog(SyncFakes.Gigs gigs, SyncFakes.Published published,
                                            SyncFakes.DirectTransactions transactions, SyncFakes.Outbox outbox,
                                            SyncRequests requests) {
        return new GigCatalogService(gigs, published, transactions, requests,
                writes(gigs, published, outbox, requests), LiveUpdates.NONE, CLOCK);
    }
}
