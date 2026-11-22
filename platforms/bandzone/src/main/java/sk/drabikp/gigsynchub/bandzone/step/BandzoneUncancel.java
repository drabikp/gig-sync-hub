package sk.drabikp.gigsynchub.bandzone.step;

import org.springframework.stereotype.Component;
import sk.drabikp.gigsynchub.bandzone.BandzonePlatform;
import sk.drabikp.gigsynchub.bandzone.BandzonePortalException;
import sk.drabikp.gigsynchub.bandzone.portal.BandzonePortalClient;
import sk.drabikp.gigsynchub.bandzone.portal.BandzoneSession;
import sk.drabikp.gigsynchub.gig.domain.Gig;
import sk.drabikp.gigsynchub.gig.domain.platform.Platform;
import sk.drabikp.gigsynchub.sync.application.port.out.OneAtATimeStep;
import sk.drabikp.gigsynchub.sync.domain.StepOutcome;
import sk.drabikp.gigsynchub.sync.domain.StepType;

import java.util.Optional;

/**
 * Bandzone's reactivate, in place: the cancelled concert is made public again (it keeps its id, its page
 * and whoever follows it), then brought up to date with the gig, which may have changed while it was
 * cancelled. A concert Bandzone won't bring back — gone, or already played — is refused, and the workflow
 * removes and creates it again instead. Once it is public again the reactivation is done even if bringing
 * it up to date fails (with a note to Re-sync): failing it would remove and create it after all.
 */
@Component
class BandzoneUncancel implements OneAtATimeStep {

    private final BandzonePortalClient portalClient;

    BandzoneUncancel(BandzonePortalClient portalClient) {
        this.portalClient = portalClient;
    }

    @Override
    public Platform platform() {
        return BandzonePlatform.PLATFORM;
    }

    @Override
    public StepType type() {
        return StepType.UNCANCEL;
    }

    @Override
    public Optional<String> refusal(Gig gig) {
        return Optional.empty();
    }

    @Override
    public StepOutcome runOne(Item item) {
        try (BandzoneSession session = portalClient.openSession(item.band())) {
            session.reactivateGig(item.externalRef());
            try {
                session.updateGig(item.externalRef(), item.gig());
                return StepOutcome.done(null);
            } catch (BandzonePortalException e) {
                return StepOutcome.done("public again, but not all details were saved (" + e.getMessage()
                        + ") — use Re-sync");
            }
        } catch (BandzonePortalException e) {
            return e.outcome();
        }
    }
}
