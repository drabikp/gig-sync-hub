package sk.drabikp.bzscraper.catalog.application.port.in;

import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.gig.domain.platform.Publication;

import java.util.Map;
import java.util.Set;

/** Where each catalog gig is published: per gig, its copy on each platform. */
public interface ListPublicationsUseCase {

    /** Gigs never published anywhere are absent. For a page, ask {@link #publicationsOf}. */
    Map<GigId, Map<Platform, Publication>> publicationsByGig(BandId band);

    /** The same, for the gigs of one page only. */
    Map<GigId, Map<Platform, Publication>> publicationsOf(BandId band, Set<GigId> gigIds);
}
