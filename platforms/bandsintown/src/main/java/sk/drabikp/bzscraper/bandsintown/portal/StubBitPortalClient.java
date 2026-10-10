package sk.drabikp.bzscraper.bandsintown.portal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.bandsintown.BitPortalException;
import sk.drabikp.bzscraper.gig.domain.BandId;

import java.time.Duration;

/**
 * Used while Bandsintown automation is switched off, so the app boots without a
 * browser: every operation fails with a message saying how to switch it on.
 */
@Component
@ConditionalOnProperty(name = "bzscraper.bandsintown.selenium.enabled", havingValue = "false", matchIfMissing = true)
class StubBitPortalClient implements BitPortalClient {

    @Override
    public BitSession openSession(BandId band, Duration wait) throws BitPortalException {
        throw BitPortalException.needsUser(
                "This server doesn't drive Bandsintown (bzscraper.bandsintown.selenium.enabled is off).");
    }
}
