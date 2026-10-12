package sk.drabikp.gigsynchub.catalog.application.port.in;

import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.gig.domain.GigId;
import sk.drabikp.gigsynchub.sync.domain.QueueResult;

/**
 * Removes a gig from the catalog and queues its deletion on every platform it is on (in
 * the same transaction). The gig's platform record stays until that deletion succeeds,
 * so the platform copy can still be found.
 */
public interface DeleteGigUseCase {

    QueueResult delete(BandId band, GigId id);
}
