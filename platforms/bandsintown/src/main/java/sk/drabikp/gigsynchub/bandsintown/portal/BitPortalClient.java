package sk.drabikp.gigsynchub.bandsintown.portal;

import sk.drabikp.gigsynchub.bandsintown.BitPortalException;
import sk.drabikp.gigsynchub.gig.domain.BandId;

import java.time.Duration;

/**
 * Outbound port for the Bandsintown artist portal (Bandsintown has no public write
 * API). A whole batch shares one authenticated {@link BitSession}; the browser
 * automation (login + authenticator code) stays at the edge so the use cases remain
 * testable with mocks.
 */
public interface BitPortalClient {

    /** How long a read the user started (import, the platform check) waits for a busy browser. */
    Duration READ_WAIT = Duration.ofMinutes(10);

    /**
     * For a sync step: logs in to the band's account (the one it connected, {@link
     * sk.drabikp.gigsynchub.bandsintown.BandsintownAccount} — or reuses a still-valid login) and opens a
     * session; a browser busy with another operation is not waited for ({@link FailureKind#BUSY} — the step
     * is postponed).
     *
     * @throws BitPortalException if login fails (the whole batch then fails), or needing the user when the
     *                            band hasn't connected Bandsintown
     */
    default BitSession openSession(BandId band) throws BitPortalException {
        return openSession(band, Duration.ZERO);
    }

    /** Like {@link #openSession(BandId)}, waiting up to {@code wait} for a busy browser. */
    BitSession openSession(BandId band, Duration wait) throws BitPortalException;
}
