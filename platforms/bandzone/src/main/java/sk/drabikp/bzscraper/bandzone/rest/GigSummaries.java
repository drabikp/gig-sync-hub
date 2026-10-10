package sk.drabikp.bzscraper.bandzone.rest;

import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.accounts.application.port.in.BandDirectory;
import sk.drabikp.bzscraper.accounts.domain.Band;
import sk.drabikp.bzscraper.bandzone.BandzonePlatform;
import sk.drabikp.bzscraper.bandzone.rest.api.GigSummaryResponse;
import sk.drabikp.bzscraper.catalog.application.port.in.ListGigsUseCase;
import sk.drabikp.bzscraper.catalog.application.port.in.ListPublicationsUseCase;
import sk.drabikp.bzscraper.gig.application.NotFoundException;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.gig.domain.platform.Platforms;
import sk.drabikp.bzscraper.gig.domain.platform.Publication;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A band's gigs from the catalog — the source of truth — with their Bandzone concert when
 * they are there; the band is found by its address name (slug), an unknown one is "not found".
 * Read from the catalog only: the server never fetches pages for whoever asks.
 */
@Component
class GigSummaries {

    private final ListGigsUseCase listGigs;
    private final ListPublicationsUseCase listPublications;
    private final Platforms platforms;
    private final BandDirectory bands;
    private final GigSummaryMapper mapper;

    GigSummaries(ListGigsUseCase listGigs, ListPublicationsUseCase listPublications, Platforms platforms,
                 BandDirectory bands, GigSummaryMapper mapper) {
        this.listGigs = listGigs;
        this.listPublications = listPublications;
        this.platforms = platforms;
        this.bands = bands;
        this.mapper = mapper;
    }

    /** The band's gigs, earliest first. */
    List<GigSummaryResponse> ofBand(String requested) {
        BandId band = bands.bySlug(requested.toLowerCase(Locale.ROOT)).map(Band::id)
                .orElseThrow(() -> new NotFoundException("No gigs for band '" + requested + "' here."));
        Map<GigId, Map<Platform, Publication>> published = listPublications.publicationsByGig(band);
        return listGigs.allGigs(band).stream()
                .sorted(Comparator.comparing(g -> g.schedule().start()))
                .map(gig -> summary(gig, published.getOrDefault(gig.id(), Map.of()).get(BandzonePlatform.PLATFORM)))
                .toList();
    }

    /** The gig with its concert on Bandzone (its id and page) when it is there. */
    private GigSummaryResponse summary(Gig gig, Publication publication) {
        String ref = publication == null || !publication.hasExternalRef() ? null : publication.externalRef();
        String url = ref == null ? null : platforms.traits(publication.platform()).eventUrl(ref);
        return mapper.toResponse(gig, ref, url);
    }
}
