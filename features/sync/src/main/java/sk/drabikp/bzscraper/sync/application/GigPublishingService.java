package sk.drabikp.bzscraper.sync.application;

import org.jmolecules.architecture.hexagonal.Application;
import sk.drabikp.bzscraper.gig.application.port.out.Transactions;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.gig.domain.platform.Platforms;
import sk.drabikp.bzscraper.sync.application.port.in.PublishGigsUseCase;
import sk.drabikp.bzscraper.sync.domain.QueueResult;
import sk.drabikp.bzscraper.sync.domain.SyncTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Queues gigs for publishing (one outbox task per gig and platform, all in one
 * transaction). The sync worker publishes them — a platform's pending publishes in one
 * batch — and records each platform's id for the gig. Idempotent: gigs already
 * published or being published there are left out.
 */
@Application
public class GigPublishingService implements PublishGigsUseCase {

    private final SyncRequests sync;
    private final Transactions transactions;
    private final Platforms platforms;

    public GigPublishingService(SyncRequests sync, Transactions transactions, Platforms platforms) {
        this.sync = sync;
        this.transactions = transactions;
        this.platforms = platforms;
    }

    @Override
    public QueueResult publish(BandId band, Set<Platform> targets, Collection<Gig> gigs) {
        QueueResult result = transactions.computeInTransaction(() -> {
            List<SyncTask> queued = new ArrayList<>();
            List<String> notQueued = new ArrayList<>();
            for (Platform platform : platforms.ordered(targets)) {
                for (Gig gig : gigs) {
                    QueueResult one = sync.publish(band, platform, gig);
                    queued.addAll(one.queued());
                    notQueued.addAll(one.notQueued());
                }
            }
            return new QueueResult(queued, notQueued);
        });
        sync.signal(band, result);
        return result;
    }
}
