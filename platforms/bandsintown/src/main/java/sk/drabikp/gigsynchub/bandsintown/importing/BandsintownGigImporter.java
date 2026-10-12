package sk.drabikp.gigsynchub.bandsintown.importing;

import org.springframework.stereotype.Component;
import sk.drabikp.gigsynchub.bandsintown.BandsintownPlatform;
import sk.drabikp.gigsynchub.bandsintown.BitPortalException;
import sk.drabikp.gigsynchub.bandsintown.portal.BitPortalClient;
import sk.drabikp.gigsynchub.bandsintown.portal.BitSession;
import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.gig.domain.platform.Platform;
import sk.drabikp.gigsynchub.importing.application.port.out.GigImporter;
import sk.drabikp.gigsynchub.importing.domain.ImportedGig;

import java.util.List;

/**
 * Imports the artist's Bandsintown events (upcoming and past) through the artist portal
 * — the same logged-in, human-paced browser session publishing uses — each with its
 * Bandsintown event id.
 */
@Component
class BandsintownGigImporter implements GigImporter {

    private final BitPortalClient portalClient;

    public BandsintownGigImporter(BitPortalClient portalClient) {
        this.portalClient = portalClient;
    }

    @Override
    public Platform platform() {
        return BandsintownPlatform.PLATFORM;
    }

    @Override
    public List<ImportedGig> importGigs(BandId band) throws BitPortalException {
        try (BitSession session = portalClient.openSession(band, BitPortalClient.READ_WAIT)) {
            return session.listEvents();
        }
    }
}
