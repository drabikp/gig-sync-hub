package sk.drabikp.bzscraper.importing.application.port.out;

import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.importing.domain.ImportedGig;
import sk.drabikp.bzscraper.sync.application.port.out.PlatformException;

import java.util.List;

/**
 * Reads all of the band's gigs on one platform — upcoming and past — each with the
 * platform's id for it, so they can be imported into the catalog and linked to the
 * existing events. One implementation per importable platform; the import use case
 * collects them all via an injected {@code List}.
 */
public interface GigImporter {

    Platform platform();

    /** @throws PlatformException with a reason for the user when the platform can't be read */
    /** The band's gigs on the platform (through the band's account there). */
    List<ImportedGig> importGigs(BandId band) throws PlatformException;
}
