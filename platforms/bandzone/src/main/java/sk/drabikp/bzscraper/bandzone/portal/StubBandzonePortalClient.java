package sk.drabikp.bzscraper.bandzone.portal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.bandzone.BandzonePortalException;
import sk.drabikp.bzscraper.gig.domain.BandId;

import java.time.Duration;

/**
 * Placeholder {@link BandzonePortalClient}: lets the app boot and the Bandzone
 * publish pipeline wire without a browser. Opening a session fails with a clear
 * message, so a publish reports every gig FAILED rather than silently doing nothing.
 * Enable the real client with {@code bzscraper.bandzone.selenium.enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "bzscraper.bandzone.selenium.enabled", havingValue = "false", matchIfMissing = true)
class StubBandzonePortalClient implements BandzonePortalClient {

    private static final Logger logger = LoggerFactory.getLogger(StubBandzonePortalClient.class);

    @Override
    public BandzoneSession openSession(BandId band, Duration wait) throws BandzonePortalException {
        logger.warn("StubBandzonePortalClient: openSession requested but no real portal client "
                + "is wired. Set bzscraper.bandzone.selenium.enabled=true to publish for real.");
        throw BandzonePortalException.needsUser(
                "This server doesn't drive Bandzone (bzscraper.bandzone.selenium.enabled is off).");
    }
}
