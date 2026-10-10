package sk.drabikp.bzscraper.sync.application;

import sk.drabikp.bzscraper.gig.application.port.out.GigRepository;
import sk.drabikp.bzscraper.gig.application.port.out.Transactions;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.sync.application.port.in.QueueSyncWork;
import sk.drabikp.bzscraper.sync.application.port.in.ResyncGigUseCase;
import sk.drabikp.bzscraper.sync.application.port.in.SyncStateUseCase;
import sk.drabikp.bzscraper.sync.domain.QueueResult;
import sk.drabikp.bzscraper.sync.domain.SyncTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Queues an update of every platform copy of the gigs, to their current catalog details. */
public class GigResyncService implements ResyncGigUseCase {

    private final GigRepository gigRepository;
    private final QueueSyncWork sync;
    private final SyncStateUseCase state;
    private final Transactions transactions;

    public GigResyncService(GigRepository gigRepository, QueueSyncWork sync, SyncStateUseCase state,
                            Transactions transactions) {
        this.gigRepository = gigRepository;
        this.sync = sync;
        this.state = state;
        this.transactions = transactions;
    }

    @Override
    public QueueResult resync(BandId band, Collection<GigId> gigs) {
        QueueResult result = transactions.computeInTransaction(() -> {
            List<SyncTask> queued = new ArrayList<>();
            List<String> notQueued = new ArrayList<>();
            for (GigId id : gigs) {
                Optional<Gig> gig = gigRepository.findById(band, id);
                if (gig.isEmpty()) {
                    continue;
                }
                if (state.busy(band, id)) {
                    notQueued.add(SyncTask.labelOf(gig.get()) + ": its platform work is running — re-sync it when "
                            + "that is done");
                    continue;
                }
                QueueResult one = sync.update(band, gig.get());
                queued.addAll(one.queued());
                notQueued.addAll(one.notQueued());
                if (one.queued().isEmpty() && one.notQueued().isEmpty()) {
                    notQueued.add(gig.get().title() + ": not on any platform, or an update is already waiting");
                }
            }
            return new QueueResult(queued, notQueued);
        });
        sync.signal(band, result);
        return result;
    }
}
