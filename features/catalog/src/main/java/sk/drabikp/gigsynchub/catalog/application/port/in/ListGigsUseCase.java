package sk.drabikp.gigsynchub.catalog.application.port.in;

import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.gig.domain.DateRange;
import sk.drabikp.gigsynchub.gig.domain.Gig;
import sk.drabikp.gigsynchub.gig.domain.GigId;
import sk.drabikp.gigsynchub.gig.domain.GigQuery;
import sk.drabikp.gigsynchub.gig.domain.GigSlice;

import java.util.List;

/** Reads gigs from the local catalog. */
public interface ListGigsUseCase {

    /** Every gig. For a page of the list, ask {@link #page} — this one reads the whole catalog. */
    List<Gig> allGigs(BandId band);

    /**
     * One page of the band's list, filtered, ordered and paged by the STORE — the page that shows it
     * does none of that. What the gig table knows nothing about (a platform state) narrows the query
     * first, through {@link GigQuery#onlyIn}.
     */
    GigSlice page(BandId band, GigQuery query);

    /** The band's gigs, earliest first and at most this many — a feed that must stay bounded. */
    List<Gig> gigsEarliestFirst(BandId band, int limit);

    /** The gig; {@link sk.drabikp.gigsynchub.gig.application.NotFoundException} when it isn't (any more). */
    Gig gig(BandId band, GigId id);

    List<Gig> gigsStartingWithin(BandId band, DateRange range);
}
