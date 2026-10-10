package sk.drabikp.bzscraper.bandzone.step;

import sk.drabikp.bzscraper.bandzone.BandzonePlatform;
import sk.drabikp.bzscraper.bandzone.BandzonePortalException;
import sk.drabikp.bzscraper.bandzone.portal.BandzonePortalClient;
import sk.drabikp.bzscraper.bandzone.portal.BandzoneSession;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.sync.application.port.out.OneAtATimeStep;
import sk.drabikp.bzscraper.sync.domain.StepOutcome;

import java.util.Optional;

/**
 * Cancels or deletes a Bandzone concert by its id through the band-admin delete tab, one at
 * a time. Takes every concert, past ones included. What it did is checked there; a note says
 * what is worth knowing (a concert already gone).
 */
abstract class BandzoneWithdrawal implements OneAtATimeStep {

    private final BandzonePortalClient portalClient;

    BandzoneWithdrawal(BandzonePortalClient portalClient) {
        this.portalClient = portalClient;
    }

    /** Cancels or deletes the concert; a note for the sync log, if any. */
    abstract Optional<String> withdraw(BandzoneSession session, String bandzoneId) throws BandzonePortalException;

    @Override
    public Platform platform() {
        return BandzonePlatform.PLATFORM;
    }

    @Override
    public StepOutcome runOne(Item item) {
        try (BandzoneSession session = portalClient.openSession(item.band())) {
            return StepOutcome.done(withdraw(session, item.externalRef()).orElse(null));
        } catch (BandzonePortalException e) {
            return e.outcome();
        }
    }
}
