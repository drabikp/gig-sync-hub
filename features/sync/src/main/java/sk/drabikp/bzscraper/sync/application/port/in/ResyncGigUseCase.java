package sk.drabikp.bzscraper.sync.application.port.in;

import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.sync.domain.QueueResult;

import java.util.Collection;

/**
 * Queues an update of the gigs' platform copies to their current catalog details — for
 * when a platform copy was changed by hand or an earlier update was given up on.
 */
public interface ResyncGigUseCase {

    QueueResult resync(BandId band, Collection<GigId> gigs);
}
