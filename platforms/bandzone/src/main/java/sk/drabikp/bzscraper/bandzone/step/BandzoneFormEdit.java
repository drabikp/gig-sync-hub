package sk.drabikp.bzscraper.bandzone.step;

import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.bandzone.BandzonePlatform;
import sk.drabikp.bzscraper.bandzone.BandzonePortalException;
import sk.drabikp.bzscraper.bandzone.portal.BandzonePortalClient;
import sk.drabikp.bzscraper.bandzone.portal.BandzoneSession;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.sync.application.port.out.OneAtATimeStep;
import sk.drabikp.bzscraper.sync.domain.StepOutcome;
import sk.drabikp.bzscraper.sync.domain.StepType;

/**
 * Bandzone's form edit: the concert's update form, one concert at a time (Bandzone has no
 * bulk upload). Takes every gig, past ones included. An error Bandzone won't get over by
 * waiting (e.g. a city it doesn't know) goes to the user with what to correct.
 */
@Component
class BandzoneFormEdit implements OneAtATimeStep {

    private final BandzonePortalClient portalClient;

    public BandzoneFormEdit(BandzonePortalClient portalClient) {
        this.portalClient = portalClient;
    }

    @Override
    public Platform platform() {
        return BandzonePlatform.PLATFORM;
    }

    @Override
    public StepType type() {
        return StepType.FORM_EDIT;
    }

    @Override
    public StepOutcome runOne(Item item) {
        try (BandzoneSession session = portalClient.openSession(item.band())) {
            session.updateGig(item.externalRef(), item.gig());
            return StepOutcome.done(null);
        } catch (BandzonePortalException e) {
            return e.outcome();
        }
    }
}
