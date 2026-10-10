package sk.drabikp.bzscraper.bandsintown.account;

import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.accounts.application.port.out.PlatformAccountCheck;
import sk.drabikp.bzscraper.accounts.domain.AccountCheck;
import sk.drabikp.bzscraper.bandsintown.BandsintownPlatform;
import sk.drabikp.bzscraper.bandsintown.BitPortalException;
import sk.drabikp.bzscraper.bandsintown.portal.BitPortalClient;
import sk.drabikp.bzscraper.bandsintown.portal.BitSession;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;

import java.time.Duration;

/**
 * Tests a band's Bandsintown account: signs in to the artist portal with it (the authenticator code too,
 * when asked) and opens its artist.
 */
@Component
class BandsintownAccountCheck implements PlatformAccountCheck {

    /** How long the test waits for the browser another operation holds. */
    private static final Duration WAIT = Duration.ofSeconds(30);

    private final BitPortalClient portal;

    BandsintownAccountCheck(BitPortalClient portal) {
        this.portal = portal;
    }

    @Override
    public Platform platform() {
        return BandsintownPlatform.PLATFORM;
    }

    @Override
    public AccountCheck.Outcome check(BandId band) {
        try (BitSession ignored = portal.openSession(band, WAIT)) {
            return AccountCheck.Outcome.signedIn("Signed in to the artist portal.");
        } catch (BitPortalException e) {
            return AccountCheck.Outcome.failed(e.getMessage());
        }
    }
}
