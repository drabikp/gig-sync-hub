package sk.drabikp.bzscraper.catalog.application;

import sk.drabikp.bzscraper.catalog.application.port.in.GigBusyException;
import sk.drabikp.bzscraper.catalog.application.port.in.GigIdentityTakenException;
import sk.drabikp.bzscraper.catalog.application.port.in.GigWrites;
import sk.drabikp.bzscraper.catalog.application.port.out.GigMovedListener;
import sk.drabikp.bzscraper.gig.application.ConcurrentChangeException;
import sk.drabikp.bzscraper.gig.application.port.out.GigRepository;
import sk.drabikp.bzscraper.gig.application.port.out.PublishedGigStore;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.sync.application.port.in.QueueSyncWork;
import sk.drabikp.bzscraper.sync.application.port.in.SyncStateUseCase;
import sk.drabikp.bzscraper.sync.domain.QueueResult;
import sk.drabikp.bzscraper.sync.domain.SyncTask;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/** The catalog's write path ({@link GigWrites}) over the repository and the sync outbox. */
public class CatalogWrites implements GigWrites {

    private final GigRepository gigRepository;
    private final PublishedGigStore publishedGigStore;
    private final List<GigMovedListener> movedListeners;
    private final QueueSyncWork sync;
    private final SyncStateUseCase syncState;

    public CatalogWrites(GigRepository gigRepository, PublishedGigStore publishedGigStore,
                         List<GigMovedListener> movedListeners, QueueSyncWork sync, SyncStateUseCase syncState) {
        this.gigRepository = gigRepository;
        this.publishedGigStore = publishedGigStore;
        this.movedListeners = List.copyOf(movedListeners);
        this.sync = sync;
        this.syncState = syncState;
    }

    @Override
    public void add(BandId band, Gig gig) {
        if (gigRepository.findById(band, gig.id()).isPresent()) {
            throw new GigIdentityTakenException(gig);
        }
        gigRepository.save(band, gig);
    }

    @Override
    public QueueResult replace(BandId band, Gig seen, Gig updated) {
        GigId originalId = seen.id();
        Gig current = gigRepository.findById(band, originalId).orElseThrow(ConcurrentChangeException::deleted);
        if (!current.equals(seen)) {
            throw ConcurrentChangeException.changed();
        }
        requireNotBusy(band, originalId, current);
        if (!updated.id().equals(originalId)) {
            if (gigRepository.findById(band, updated.id()).isPresent()) {
                throw new GigIdentityTakenException(updated);
            }
            gigRepository.deleteById(band, originalId);
            publishedGigStore.move(band, originalId, updated.id());
            movedListeners.forEach(listener -> listener.gigMoved(band, originalId, updated.id()));
            sync.move(band, originalId, updated);
        }
        gigRepository.save(band, updated);
        return current.equals(updated) ? QueueResult.NOTHING : sync.update(band, updated);
    }

    @Override
    public QueueResult delete(BandId band, GigId id) {
        Gig gig = gigRepository.findById(band, id).orElse(null);
        requireNotBusy(band, id, gig);
        gigRepository.deleteById(band, id);
        return sync.delete(band, id, gig);
    }

    @Override
    public QueueResult cancel(BandId band, GigId id) {
        return turn(band, id, Gig::cancelled, Gig::cancel, gig -> sync.cancel(band, gig));
    }

    @Override
    public QueueResult reactivate(BandId band, GigId id) {
        return turn(band, id, gig -> !gig.cancelled(), Gig::reactivate, gig -> sync.reactivate(band, gig));
    }

    @Override
    public void link(BandId band, Platform platform, GigId id, String externalRef) {
        publishedGigStore.record(band, platform, id, externalRef);
    }

    @Override
    public void forgetLink(BandId band, Platform platform, GigId id) {
        requireNotBusy(band, id, gigRepository.findById(band, id).orElse(null));
        publishedGigStore.remove(band, platform, id);
    }

    /**
     * Cancels or takes back the band's gig ({@code change}) and queues that on its platforms ({@code queue});
     * nothing when it is gone or {@code already} so.
     */
    private QueueResult turn(BandId band, GigId id, Predicate<Gig> already, UnaryOperator<Gig> change,
                             Function<Gig, QueueResult> queue) {
        Optional<Gig> stored = gigRepository.findById(band, id);
        if (stored.isEmpty() || already.test(stored.get())) {
            return QueueResult.NOTHING;
        }
        requireNotBusy(band, id, stored.get());
        Gig changed = change.apply(stored.get());
        gigRepository.save(band, changed);
        return queue.apply(changed);
    }

    /** Refuses a gig whose platform work is running ({@code gig} names it; null when it is gone from the catalog). */
    private void requireNotBusy(BandId band, GigId id, Gig gig) {
        if (syncState.busy(band, id)) {
            throw new GigBusyException(gig != null ? SyncTask.labelOf(gig) : SyncTask.labelOf(id));
        }
    }
}
