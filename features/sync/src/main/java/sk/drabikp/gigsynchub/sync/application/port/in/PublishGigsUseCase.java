package sk.drabikp.gigsynchub.sync.application.port.in;

import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.gig.domain.Gig;
import sk.drabikp.gigsynchub.gig.domain.platform.Platform;
import sk.drabikp.gigsynchub.sync.domain.QueueResult;

import java.util.Collection;
import java.util.Set;

/**
 * Queues gigs for publishing on one or more platforms. Gigs already published (or
 * already queued) there, and cancelled gigs, are left out with the reason.
 */
public interface PublishGigsUseCase {

    QueueResult publish(BandId band, Set<Platform> targets, Collection<Gig> gigs);
}
