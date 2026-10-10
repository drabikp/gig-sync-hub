package sk.drabikp.bzscraper.bandsintown.portal;

import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.bandsintown.BitPortalException;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.portal.OnPlatformClient;
import sk.drabikp.bzscraper.portal.PlatformClient;

import java.time.Duration;

/**
 * Used while Bandsintown automation is switched off, so the app boots without a
 * browser: every operation fails with a message saying how to switch it on.
 */
@Component
@OnPlatformClient(platform = "bandsintown", value = PlatformClient.OFF)
class StubBitPortalClient implements BitPortalClient {

    @Override
    public BitSession openSession(BandId band, Duration wait) throws BitPortalException {
        throw BitPortalException.needsUser(
                "This server doesn't drive Bandsintown (bzscraper.bandsintown.client is off).");
    }
}
