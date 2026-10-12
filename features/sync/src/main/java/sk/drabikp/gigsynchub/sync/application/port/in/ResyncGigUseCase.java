package sk.drabikp.gigsynchub.sync.application.port.in;

import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.gig.domain.GigId;
import sk.drabikp.gigsynchub.sync.domain.QueueResult;

import java.util.Collection;

/**
 * Queues an update of the gigs' platform copies to their current catalog details — for
 * when a platform copy was changed by hand or an earlier update was given up on.
 */
public interface ResyncGigUseCase {

    QueueResult resync(BandId band, Collection<GigId> gigs);
}
