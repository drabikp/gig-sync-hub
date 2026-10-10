package sk.drabikp.bzscraper.bandzone.portal;

import sk.drabikp.bzscraper.bandzone.BandzonePortalException;
import sk.drabikp.bzscraper.gig.domain.BandId;

import java.time.Duration;

/**
 * Outbound port for the Bandzone band-admin interaction. Bandzone has no bulk
 * import, so gigs are created one at a time through its 2-step JS wizard — but a
 * whole batch shares one authenticated {@link BandzoneSession} (one login), not one
 * login per gig. Keeps the browser automation at the edge so the publish use case
 * stays testable.
 */
public interface BandzonePortalClient {

    /**
     * For a sync step: logs in to the band's account (the one it connected, {@link
     * sk.drabikp.bzscraper.bandzone.BandzoneAccount}) and opens a session; a browser busy with another
     * operation is not waited for ({@link FailureKind#BUSY} — the step is postponed).
     *
     * @throws BandzonePortalException if login fails (the whole batch then fails), or needing the user
     *                                 when the band hasn't connected Bandzone
     */
    default BandzoneSession openSession(BandId band) throws BandzonePortalException {
        return openSession(band, Duration.ZERO);
    }

    /** Like {@link #openSession(BandId)}, waiting up to {@code wait} for a busy browser. */
    BandzoneSession openSession(BandId band, Duration wait) throws BandzonePortalException;
}
