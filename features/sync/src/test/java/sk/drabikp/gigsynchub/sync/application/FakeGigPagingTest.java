package sk.drabikp.gigsynchub.sync.application;

import sk.drabikp.gigsynchub.gig.application.port.out.GigPagingContract;
import sk.drabikp.gigsynchub.gig.application.port.out.GigRepository;
import sk.drabikp.gigsynchub.gig.application.port.out.PublishedGigStore;

/**
 * The in-memory store answering the same {@link GigPagingContract} as the database's, so a service
 * test cannot see a list the real one would never answer.
 */
class FakeGigPagingTest extends GigPagingContract {

    private final SyncFakes.Published published = new SyncFakes.Published();
    private final SyncFakes.Gigs gigs = new SyncFakes.Gigs().publishedIn(published);

    @Override
    protected GigRepository repository() {
        return gigs;
    }

    @Override
    protected PublishedGigStore publications() {
        return published;
    }
}
