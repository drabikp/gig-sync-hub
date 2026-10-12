package sk.drabikp.gigsynchub.bandzone.account;

import org.springframework.stereotype.Component;
import sk.drabikp.gigsynchub.accounts.application.port.out.PlatformAccountCheck;
import sk.drabikp.gigsynchub.accounts.domain.AccountCheck;
import sk.drabikp.gigsynchub.bandzone.BandzonePlatform;
import sk.drabikp.gigsynchub.bandzone.BandzonePortalException;
import sk.drabikp.gigsynchub.bandzone.portal.BandzonePortalClient;
import sk.drabikp.gigsynchub.bandzone.portal.BandzoneSession;
import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.gig.domain.platform.Platform;

import java.time.Duration;

/** Tests a band's Bandzone account: signs in with it and reads the band's page at the address it names. */
@Component
class BandzoneAccountCheck implements PlatformAccountCheck {

    /** How long the test waits for the browser another operation holds. */
    private static final Duration WAIT = Duration.ofSeconds(30);

    private final BandzonePortalClient portal;

    BandzoneAccountCheck(BandzonePortalClient portal) {
        this.portal = portal;
    }

    @Override
    public Platform platform() {
        return BandzonePlatform.PLATFORM;
    }

    @Override
    public AccountCheck.Outcome check(BandId band) {
        try (BandzoneSession session = portal.openSession(band, WAIT)) {
            return AccountCheck.Outcome.signedIn("Signed in; the band is " + session.bandName() + ".");
        } catch (BandzonePortalException e) {
            return AccountCheck.Outcome.failed(e.getMessage());
        }
    }
}
