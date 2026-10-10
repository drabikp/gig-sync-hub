package sk.drabikp.bzscraper.catalog.application;

import sk.drabikp.bzscraper.catalog.application.port.in.CancelGigUseCase;
import sk.drabikp.bzscraper.catalog.application.port.in.DeleteGigUseCase;
import sk.drabikp.bzscraper.catalog.application.port.in.GigWrites;
import sk.drabikp.bzscraper.catalog.application.port.in.ListGigsUseCase;
import sk.drabikp.bzscraper.catalog.application.port.in.ListPublicationsUseCase;
import sk.drabikp.bzscraper.catalog.application.port.in.SaveGigUseCase;
import sk.drabikp.bzscraper.catalog.application.port.in.UpdateGigUseCase;
import sk.drabikp.bzscraper.gig.application.NotFoundException;
import sk.drabikp.bzscraper.gig.application.port.out.GigRepository;
import sk.drabikp.bzscraper.gig.application.port.out.LiveUpdates;
import sk.drabikp.bzscraper.gig.application.port.out.PublishedGigStore;
import sk.drabikp.bzscraper.gig.application.port.out.Transactions;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.DateRange;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.gig.domain.platform.Publication;
import sk.drabikp.bzscraper.sync.application.port.in.QueueSyncWork;
import sk.drabikp.bzscraper.sync.domain.QueueResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * Manages the local gig catalog (the source of truth) over the {@link GigRepository}.
 * Thin application service — invariants live in the {@link Gig} aggregate, persistence
 * behind the repository port. Every change to a gig that is on a platform queues the
 * matching platform work in the sync outbox IN THE SAME TRANSACTION (see
 * {@link QueueSyncWork}), so the catalog and its pending platform work never disagree;
 * the platforms catch up when the sync worker runs. An edit that changes the gig's
 * identity also re-keys its published records, its pending tasks and its calendar links.
 * Every change is announced on the live updates ({@link LiveUpdates.Topic#GIGS}).
 */
public class GigCatalogService
        implements SaveGigUseCase, ListGigsUseCase, DeleteGigUseCase, UpdateGigUseCase, CancelGigUseCase,
        ListPublicationsUseCase {

    private final GigRepository gigRepository;
    private final PublishedGigStore publishedGigStore;
    private final Transactions transactions;
    private final QueueSyncWork sync;
    private final GigWrites writes;
    private final LiveUpdates live;

    public GigCatalogService(GigRepository gigRepository, PublishedGigStore publishedGigStore,
                             Transactions transactions, QueueSyncWork sync, GigWrites writes, LiveUpdates live) {
        this.gigRepository = gigRepository;
        this.publishedGigStore = publishedGigStore;
        this.transactions = transactions;
        this.sync = sync;
        this.writes = writes;
        this.live = live;
    }

    @Override
    public void add(BandId band, Gig gig) {
        transactions.inTransaction(() -> writes.add(band, gig));
        live.changed(band, LiveUpdates.Topic.GIGS);
    }

    @Override
    public Gig gig(BandId band, GigId id) {
        return gigRepository.findById(band, id).orElseThrow(() -> new NotFoundException("No such gig."));
    }

    @Override
    public List<Gig> allGigs(BandId band) {
        return gigRepository.findAll(band);
    }

    @Override
    public List<Gig> gigsStartingWithin(BandId band, DateRange range) {
        return gigRepository.findStartingWithin(band, range);
    }

    @Override
    public Map<GigId, Map<Platform, Publication>> publicationsByGig(BandId band) {
        Map<GigId, Map<Platform, Publication>> byGig = new HashMap<>();
        for (Publication p : publishedGigStore.all(band)) {
            byGig.computeIfAbsent(p.gigId(), id -> new TreeMap<>()).put(p.platform(), p);
        }
        return byGig;
    }

    @Override
    public QueueResult delete(BandId band, GigId id) {
        return written(band, () -> writes.delete(band, id));
    }

    @Override
    public QueueResult update(BandId band, Gig seen, Gig updated) {
        return written(band, () -> writes.replace(band, seen, updated));
    }

    @Override
    public QueueResult cancel(BandId band, GigId id) {
        return written(band, () -> writes.cancel(band, id));
    }

    @Override
    public QueueResult reactivate(BandId band, GigId id) {
        return written(band, () -> writes.reactivate(band, id));
    }

    /** Writes in one transaction, then tells the sync and the band's pages. */
    private QueueResult written(BandId band, Supplier<QueueResult> write) {
        QueueResult queued = transactions.computeInTransaction(write);
        sync.signal(band, queued);
        live.changed(band, LiveUpdates.Topic.GIGS);
        return queued;
    }
}
