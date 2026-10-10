package sk.drabikp.bzscraper.bandsintown.step;

import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.bandsintown.BandsintownPlatform;
import sk.drabikp.bzscraper.bandsintown.BitPortalException;
import sk.drabikp.bzscraper.bandsintown.portal.BitPortalClient;
import sk.drabikp.bzscraper.bandsintown.portal.BitSession;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.sync.application.port.out.OneAtATimeStep;
import sk.drabikp.bzscraper.sync.domain.StepOutcome;
import sk.drabikp.bzscraper.sync.domain.StepType;

/**
 * Bandsintown's form edit: one event at a time, changed the way the portal's own edit form changes
 * one — what the bulk upload can't do: past events (the upload refuses them) and rows it refused.
 * Sets the place (the gig's town as the place search found it), dates, times, name and description.
 * A cancelled gig needs nothing: cancelling removed it.
 */
@Component
class BandsintownFormEdit implements OneAtATimeStep {

    private final BitPortalClient portalClient;

    public BandsintownFormEdit(BitPortalClient portalClient) {
        this.portalClient = portalClient;
    }

    @Override
    public Platform platform() {
        return BandsintownPlatform.PLATFORM;
    }

    @Override
    public StepType type() {
        return StepType.FORM_EDIT;
    }

    @Override
    public StepOutcome runOne(Item item) {
        if (item.gig().cancelled()) {
            return StepOutcome.done("cancelled — Bandsintown keeps no cancelled copy to edit");
        }
        try (BitSession session = portalClient.openSession(item.band())) {
            return StepOutcome.done(session.editEventInForm(item.externalRef(), item.gig()).orElse(null));
        } catch (BitPortalException e) {
            return e.outcome();
        }
    }
}
