package sk.drabikp.gigsynchub.catalog.application.port.out;

import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.gig.domain.Gig;
import sk.drabikp.gigsynchub.gig.domain.platform.Platform;

import java.util.List;

/** Writes gigs into a file a platform imports by hand (its own format). */
public interface GigExporter {

    Platform platform();

    /** The downloaded file's name, e.g. "gigs.csv". */
    String fileName();

    String mediaType();

    String export(BandId band, List<Gig> gigs);
}
