package sk.drabikp.gigsynchub.bandzone.portal;

import sk.drabikp.gigsynchub.bandzone.BandzonePortalException;
import sk.drabikp.gigsynchub.gig.domain.Gig;

/**
 * An authenticated Bandzone session that can create multiple gigs without logging
 * in again. Opened once per batch (see {@link BandzonePortalClient#openSession})
 * and closed when done — turning an N-gig publish from N logins into one.
 */
public interface BandzoneSession extends AutoCloseable {

    /**
     * Creates one gig in this already-authenticated session.
     *
     * @return the Bandzone concert id of the created gig (for later update/cancel/delete)
     * @throws BandzonePortalException if this gig's wizard fails (other gigs in the
     *                                 batch are unaffected)
     */
    String createGig(Gig gig) throws BandzonePortalException;

    /**
     * Overwrites an existing concert's details (title, start, city, admission,
     * description, Facebook link) in place; its Bandzone id does not change. Does not
     * touch the cancelled state — Bandzone has no way to un-cancel a concert.
     */
    void updateGig(String bandzoneId, Gig gig) throws BandzonePortalException;

    /**
     * Marks the concert cancelled on Bandzone (it stays listed as cancelled), checked on the
     * band's page.
     *
     * @throws BandzonePortalException needing the user when the concert is gone from Bandzone
     */
    void cancelGig(String bandzoneId) throws BandzonePortalException;

    /**
     * Deletes the concert from Bandzone entirely, checked afterwards. A concert already gone is
     * not an error.
     *
     * @return false when it was already gone
     */
    boolean deleteGig(String bandzoneId) throws BandzonePortalException;

    /** The band's name on Bandzone, from its page — which shows the band's address is right. */
    String bandName() throws BandzonePortalException;

    /** Releases the session (closes the browser). Never throws. */
    @Override
    void close();
}
