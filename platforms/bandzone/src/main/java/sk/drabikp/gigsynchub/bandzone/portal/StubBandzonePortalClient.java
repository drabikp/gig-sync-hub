package sk.drabikp.gigsynchub.bandzone.portal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import sk.drabikp.gigsynchub.bandzone.BandzonePortalException;
import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.portal.OnPlatformClient;
import sk.drabikp.gigsynchub.portal.PlatformClient;

import java.time.Duration;

/**
 * Placeholder {@link BandzonePortalClient}: lets the app boot and the Bandzone
 * publish pipeline wire without a browser. Opening a session fails with a clear
 * message, so a publish reports every gig FAILED rather than silently doing nothing.
 * The real client: {@code gigsynchub.bandzone.client=http}.
 */
@Component
@OnPlatformClient(platform = "bandzone", value = PlatformClient.OFF)
class StubBandzonePortalClient implements BandzonePortalClient {

    private static final Logger logger = LoggerFactory.getLogger(StubBandzonePortalClient.class);

    @Override
    public BandzoneSession openSession(BandId band, Duration wait) throws BandzonePortalException {
        logger.warn("StubBandzonePortalClient: openSession requested but no real portal client "
                + "is wired. Set gigsynchub.bandzone.client=http to publish for real.");
        throw BandzonePortalException.needsUser(
                "This server doesn't drive Bandzone (gigsynchub.bandzone.client is off).");
    }
}
