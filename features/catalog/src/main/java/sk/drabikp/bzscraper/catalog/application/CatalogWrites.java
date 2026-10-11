package sk.drabikp.bzscraper.catalog.application;

import sk.drabikp.bzscraper.catalog.application.port.in.GigBusyException;
import sk.drabikp.bzscraper.catalog.application.port.in.GigIdentityTakenException;
import sk.drabikp.bzscraper.catalog.application.port.in.GigWrites;
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

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/** The catalog's write path ({@link GigWrites}) over the repository and the sync outbox. */
public class CatalogWrites implements GigWrites {

    private final GigRepository gigRepository;
    private final PublishedGigStore publishedGigStore;
    private final QueueSyncWork sync;
    private final SyncStateUseCase syncState;

    public CatalogWrites(GigRepository gigRepository, PublishedGigStore publishedGigStore, QueueSyncWork sync,
                         SyncStateUseCase syncState) {
        this.gigRepository = gigRepository;
        this.publishedGigStore = publishedGigStore;
        this.sync = sync;
        this.syncState = syncState;
    }

    @Override
    public void add(BandId band, Gig gig) {
        // a new gig's identity is one no gig has, so this only catches a writer adding a gig it
        // already added — which would overwrite it, and that is what replace is for
        if (gigRepository.findById(band, gig.id()).isPresent()) {
            throw new GigIdentityTakenException(gig);
        }
        requireTheDayAndVenueAreFree(band, gig);
        gigRepository.save(band, gig);
    }

    /**
     * The gig keeps its identity through every edit, so nothing that refers to it is touched here —
     * no record is re-keyed, no queued work is moved, nobody is told the gig "moved". An edit that
     * puts the gig on a day and venue the band already has another gig on is still refused: that is
     * the same concert, and merging two of them is not an edit's business.
     */
    @Override
    public QueueResult replace(BandId band, Gig seen, Gig updated) {
        GigId id = seen.id();
        Gig current = gigRepository.findById(band, id).orElseThrow(ConcurrentChangeException::deleted);
        if (!current.equals(seen)) {
            throw ConcurrentChangeException.changed();
        }
        if (!updated.id().equals(id)) {
            throw new IllegalArgumentException("an edit says what a gig is, never which gig: " + id
                    + " was given the details of " + updated.id());
        }
        requireNotBusy(band, id, current);
        if (!updated.match().equals(current.match())) {
            requireTheDayAndVenueAreFree(band, updated);
        }
        gigRepository.save(band, updated);
        if (current.equals(updated)) {
            return QueueResult.NOTHING;
        }
        sync.relabel(band, updated);        // the work still waiting names the gig as it reads now
        return sync.update(band, updated);
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
     * Refuses a gig on a day and at a venue the band has another gig on ({@link Gig#match()}) — the
     * one thing a band cannot have twice, and what the store's own unique key says as well.
     */
    private void requireTheDayAndVenueAreFree(BandId band, Gig gig) {
        gigRepository.findByMatch(band, gig.match())
                .filter(other -> !other.id().equals(gig.id()))
                .ifPresent(other -> {
                    throw new GigIdentityTakenException(gig);
                });
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

    /**
     * Refuses a gig whose platform work is running ({@code gig} names it; null when it is already gone
     * from the catalog, which only leaves "the gig" to call it — an identity says nothing about a gig
     * by design, and the one who asked knows which gig they asked about).
     */
    private void requireNotBusy(BandId band, GigId id, Gig gig) {
        if (syncState.busy(band, id)) {
            throw new GigBusyException(gig != null ? SyncTask.labelOf(gig) : "The gig");
        }
    }
}
