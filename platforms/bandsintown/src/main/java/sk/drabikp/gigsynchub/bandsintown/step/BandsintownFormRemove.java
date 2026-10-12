package sk.drabikp.gigsynchub.bandsintown.step;

import org.springframework.stereotype.Component;
import sk.drabikp.gigsynchub.bandsintown.BitPortalException;
import sk.drabikp.gigsynchub.bandsintown.RemovalReason;
import sk.drabikp.gigsynchub.bandsintown.portal.BitPortalClient;
import sk.drabikp.gigsynchub.bandsintown.portal.BitSession;
import sk.drabikp.gigsynchub.sync.domain.StepType;

import java.time.Clock;

/** Bandsintown's remove through the event's form (reason "other") — past events too. */
@Component
class BandsintownFormRemove extends BandsintownRemoval {

    public BandsintownFormRemove(BitPortalClient portalClient, Clock clock) {
        super(portalClient, clock, RemovalReason.OTHER);
    }

    @Override
    void remove(BitSession session, String eventId, RemovalReason reason) throws BitPortalException {
        session.deleteEventInForm(eventId, reason);
    }

    @Override
    boolean takesPast() {
        return true;
    }

    @Override
    public StepType type() {
        return StepType.FORM_REMOVE;
    }
}
