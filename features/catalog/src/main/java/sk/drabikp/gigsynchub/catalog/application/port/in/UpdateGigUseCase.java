package sk.drabikp.gigsynchub.catalog.application.port.in;

import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.gig.domain.Gig;
import sk.drabikp.gigsynchub.sync.domain.QueueResult;

/**
 * Replaces an existing catalog gig with an edited version. If the edit changes the
 * gig's identity (its date or venue), the old entry is removed so exactly one row
 * represents the gig. Every platform the gig is on gets an update queued in the same
 * transaction; the platforms follow when the sync worker gets to it.
 *
 * <p>{@code seen} is the gig as the user saw it when they started editing: if the catalog's
 * gig is no longer that (changed or deleted meanwhile), nothing is changed and
 * {@link sk.drabikp.gigsynchub.gig.application.ConcurrentChangeException} is thrown — the user reloads and edits again.
 */
public interface UpdateGigUseCase {

    QueueResult update(BandId band, Gig seen, Gig updated);
}
