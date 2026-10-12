package sk.drabikp.gigsynchub.bandsintown.step;

import org.springframework.stereotype.Component;
import sk.drabikp.gigsynchub.accounts.application.port.in.PlatformAccounts;
import sk.drabikp.gigsynchub.bandsintown.BandsintownAccount;
import sk.drabikp.gigsynchub.bandsintown.BandsintownPlatform;
import sk.drabikp.gigsynchub.bandsintown.BitPortalException;
import sk.drabikp.gigsynchub.bandsintown.portal.BitPortalClient;
import sk.drabikp.gigsynchub.bandsintown.portal.BitSession;
import sk.drabikp.gigsynchub.gig.domain.Gig;
import sk.drabikp.gigsynchub.gig.domain.platform.Platform;
import sk.drabikp.gigsynchub.sync.application.port.out.SyncStep;
import sk.drabikp.gigsynchub.sync.domain.StepOutcome;
import sk.drabikp.gigsynchub.sync.domain.StepType;

import java.util.List;

/**
 * Bandsintown's bulk upload: one CSV for up to 25 gigs creates drafts, which are then
 * published (followers notified only when the band's account says so) and read back. Past events are taken
 * — they are listed under Past Events. A refused upload created nothing; an event that was
 * uploaded but isn't published exists as a draft, so it is left for the user.
 */
@Component
class BandsintownBulkCreate implements SyncStep {

    private final BitPortalClient portalClient;
    private final PlatformAccounts accounts;

    public BandsintownBulkCreate(BitPortalClient portalClient, PlatformAccounts accounts) {
        this.portalClient = portalClient;
        this.accounts = accounts;
    }

    @Override
    public Platform platform() {
        return BandsintownPlatform.PLATFORM;
    }

    @Override
    public StepType type() {
        return StepType.BULK_CREATE;
    }

    @Override
    public int batchSize() {
        return BitSession.MAX_ROWS_PER_UPLOAD;
    }

    @Override
    public List<StepOutcome> run(List<Item> items) {
        List<Gig> gigs = items.stream().map(Item::gig).toList();
        try (BitSession session = portalClient.openSession(items.getFirst().band())) {
            boolean notifyFollowers = BandsintownAccount.of(accounts, items.getFirst().band()).notifyFollowers();
            List<BitSession.Created> created = session.createEvents(gigs, notifyFollowers);
            return created.stream().map(BitSession.Created::outcome).toList();
        } catch (BitPortalException e) {
            // a refused upload made nothing (Bandsintown's row errors); anything else may have
            StepOutcome outcome = e.outcome();
            return gigs.stream().map(gig -> outcome).toList();
        }
    }

}
