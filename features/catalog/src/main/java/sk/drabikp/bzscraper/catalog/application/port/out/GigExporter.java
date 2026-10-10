package sk.drabikp.bzscraper.catalog.application.port.out;

import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;

import java.util.List;

/** Writes gigs into a file a platform imports by hand (its own format). */
public interface GigExporter {

    Platform platform();

    /** The downloaded file's name, e.g. "gigs.csv". */
    String fileName();

    String mediaType();

    String export(BandId band, List<Gig> gigs);
}
