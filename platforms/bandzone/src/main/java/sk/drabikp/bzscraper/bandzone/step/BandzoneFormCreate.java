package sk.drabikp.bzscraper.bandzone.step;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.bandzone.BandzonePlatform;
import sk.drabikp.bzscraper.bandzone.BandzonePortalException;
import sk.drabikp.bzscraper.bandzone.portal.BandzonePortalClient;
import sk.drabikp.bzscraper.bandzone.portal.BandzoneSession;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.sync.application.port.out.OneAtATimeStep;
import sk.drabikp.bzscraper.sync.domain.StepOutcome;
import sk.drabikp.bzscraper.sync.domain.StepType;

import java.util.Optional;

/**
 * Bandzone's create: the 2-step wizard takes only the basics, so each new concert is then
 * completed through the edit form (end, venue, poster, lineup). One gig at a time — each
 * new concert is recorded before the next is made. Once created, the gig is done even if
 * completing it fails (with a note to Re-sync): failing it would make it twice.
 */
@Component
class BandzoneFormCreate implements OneAtATimeStep {

    private static final Logger logger = LoggerFactory.getLogger(BandzoneFormCreate.class);

    private final BandzonePortalClient portalClient;

    public BandzoneFormCreate(BandzonePortalClient portalClient) {
        this.portalClient = portalClient;
    }

    @Override
    public Platform platform() {
        return BandzonePlatform.PLATFORM;
    }

    @Override
    public StepType type() {
        return StepType.FORM_CREATE;
    }

    @Override
    public Optional<String> refusal(Gig gig) {
        return Optional.empty();                 // Bandzone keeps past concerts too
    }

    /**
     * Creates and completes the concert. The session is closed by hand, not with
     * try-with-resources: nothing thrown while closing may replace the outcome of a concert that
     * was made — it would be made again.
     */
    @Override
    public StepOutcome runOne(Item item) {
        Gig gig = item.gig();
        BandzoneSession session;
        try {
            session = portalClient.openSession(item.band());
        } catch (BandzonePortalException e) {
            return e.outcome();
        }
        try {
            String bandzoneId = session.createGig(gig);
            try {
                session.updateGig(bandzoneId, gig);
                return StepOutcome.created(bandzoneId, null);
            } catch (BandzonePortalException e) {
                return StepOutcome.created(bandzoneId,
                        "created, but not all details were saved (" + e.getMessage() + ") — use Re-sync");
            }
        } catch (BandzonePortalException e) {
            // a permanent error (e.g. a town Bandzone doesn't know) stopped it before creating
            return e.outcome();
        } finally {
            try {
                session.close();
            } catch (RuntimeException e) {
                logger.warn("Closing the Bandzone session failed", e);
            }
        }
    }
}
