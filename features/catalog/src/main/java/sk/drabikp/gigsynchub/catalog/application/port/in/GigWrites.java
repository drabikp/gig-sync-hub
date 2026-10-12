package sk.drabikp.gigsynchub.catalog.application.port.in;

import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.gig.domain.Gig;
import sk.drabikp.gigsynchub.gig.domain.GigId;
import sk.drabikp.gigsynchub.gig.domain.platform.Platform;
import sk.drabikp.gigsynchub.sync.domain.QueueResult;

/**
 * The one way gigs and their platform links are written — the add page, edits, cancel and delete,
 * the calendar, Import and the platform check all go through here, so the catalog's rules hold
 * for every writer:
 * <ul>
 *   <li>a gig never overwrites another: a new gig, or an edit that moves a gig onto the day and
 *       venue of another of the band's gigs — the same concert — is refused
 *       ({@link GigIdentityTakenException});</li>
 *   <li>an edit applies only to the gig as the writer saw it (changed or deleted meanwhile →
 *       {@code ConcurrentChangeException});</li>
 *   <li>a gig whose platform work is running is not changed, deleted or unlinked ({@link GigBusyException});</li>
 *   <li>a gig keeps its identity through every edit, so its publications, its queued work and the
 *       calendar's link to it need no following; every change queues the platform work it needs
 *       where the gig is published.</li>
 * </ul>
 * Call inside the caller's transaction; the queued work is the caller's to signal once it committed.
 */
public interface GigWrites {

    /** A new gig; refused when another gig already has its identity. */
    void add(BandId band, Gig gig);

    /** Replaces the gig {@code seen} with {@code updated} and queues the update where it is published. */
    QueueResult replace(BandId band, Gig seen, Gig updated);

    /** Deletes the gig (if it is still there) and queues its removal where it is published. */
    QueueResult delete(BandId band, GigId id);

    /** Cancels the gig; nothing when it is gone or cancelled already. */
    QueueResult cancel(BandId band, GigId id);

    /** Takes a cancelled gig back; nothing when it is gone or not cancelled. */
    QueueResult reactivate(BandId band, GigId id);

    /** Records that the gig is on the platform as {@code externalRef} (an event found there, e.g. by Import). */
    void link(BandId band, Platform platform, GigId id, String externalRef);

    /** Forgets that the gig is on the platform (the event there is gone; a Publish creates it again). */
    void forgetLink(BandId band, Platform platform, GigId id);
}
