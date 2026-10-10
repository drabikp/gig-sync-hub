package sk.drabikp.bzscraper.bandsintown.importing;

import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.bandsintown.BandsintownPlatform;
import sk.drabikp.bzscraper.bandsintown.BitPortalException;
import sk.drabikp.bzscraper.bandsintown.portal.BitPortalClient;
import sk.drabikp.bzscraper.bandsintown.portal.BitSession;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.importing.application.port.out.GigImporter;
import sk.drabikp.bzscraper.importing.domain.ImportedGig;

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
