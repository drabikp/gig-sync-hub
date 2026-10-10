package sk.drabikp.bzscraper.catalog.application.port.in;

import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.DateRange;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.GigId;

import java.util.List;

/** Reads gigs from the local catalog. */
public interface ListGigsUseCase {

    List<Gig> allGigs(BandId band);

    /** The gig; {@link sk.drabikp.bzscraper.gig.application.NotFoundException} when it isn't (any more). */
    Gig gig(BandId band, GigId id);

    List<Gig> gigsStartingWithin(BandId band, DateRange range);
}
