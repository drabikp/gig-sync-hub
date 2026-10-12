package sk.drabikp.gigsynchub.bandsintown.step;

import org.springframework.stereotype.Component;
import sk.drabikp.gigsynchub.bandsintown.BitPortalException;
import sk.drabikp.gigsynchub.bandsintown.RemovalReason;
import sk.drabikp.gigsynchub.bandsintown.portal.BitPortalClient;
import sk.drabikp.gigsynchub.bandsintown.portal.BitSession;
import sk.drabikp.gigsynchub.sync.domain.StepType;

import java.time.Clock;

/** Bandsintown's cancel: it has no cancelled state, so the event is removed as "canceled". */
@Component
class BandsintownCancel extends BandsintownRemoval {

    public BandsintownCancel(BitPortalClient portalClient, Clock clock) {
        super(portalClient, clock, RemovalReason.CANCELED);
    }

    @Override
    void remove(BitSession session, String eventId, RemovalReason reason) throws BitPortalException {
        session.deleteEvent(eventId, reason);
    }

    @Override
    boolean takesPast() {
        return false;
    }

    @Override
    public StepType type() {
        return StepType.CANCEL;
    }
}
