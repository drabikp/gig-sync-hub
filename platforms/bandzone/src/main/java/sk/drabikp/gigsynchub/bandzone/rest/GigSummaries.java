package sk.drabikp.gigsynchub.bandzone.rest;

import org.springframework.stereotype.Component;
import sk.drabikp.gigsynchub.accounts.application.port.in.BandDirectory;
import sk.drabikp.gigsynchub.accounts.domain.Band;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sk.drabikp.gigsynchub.bandzone.BandzonePlatform;
import sk.drabikp.gigsynchub.bandzone.rest.api.GigSummaryResponse;
import sk.drabikp.gigsynchub.catalog.application.port.in.ListGigsUseCase;
import sk.drabikp.gigsynchub.catalog.application.port.in.ListPublicationsUseCase;
import sk.drabikp.gigsynchub.gig.application.NotFoundException;
import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.gig.domain.Gig;
import sk.drabikp.gigsynchub.gig.domain.GigId;
import sk.drabikp.gigsynchub.gig.domain.platform.Platform;
import sk.drabikp.gigsynchub.gig.domain.platform.Platforms;
import sk.drabikp.gigsynchub.gig.domain.platform.Publication;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Locale;
import java.util.Map;

/**
 * A band's gigs from the catalog — the source of truth — with their Bandzone concert when
 * they are there; the band is found by its address name (slug) — only a band that lists its gigs publicly
 * (its setting, off for a new band); an unknown one or one that doesn't is "not found".
 * Read from the catalog only: the server never fetches pages for whoever asks.
 */
@Component
class GigSummaries {

    private static final Logger log = LoggerFactory.getLogger(GigSummaries.class);

    /**
     * How many gigs the public list answers at most. Twenty years of a busy band, so no real catalog
     * meets it — it is there because the address takes no sign-in, and an answer that grows for ever
     * is an answer someone can ask for over and over.
     */
    private static final int MOST_A_BAND_PLAYS = 2000;

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

    /**
     * The band's gigs, earliest first — as many as any band plays and no more. This address is PUBLIC
     * and needs no sign-in, so it answers a bounded, store-ordered read rather than however much a
     * catalog has grown to: the rows come back in the list's order off its index, and only those
     * rows' publications are read.
     */
    List<GigSummaryResponse> ofBand(String requested) {
        BandId band = bands.bySlug(requested.toLowerCase(Locale.ROOT)).filter(Band::publicList).map(Band::id)
                .orElseThrow(() -> new NotFoundException("No gigs for band '" + requested + "' here."));
        List<Gig> gigs = listGigs.gigsEarliestFirst(band, MOST_A_BAND_PLAYS);
        if (gigs.size() == MOST_A_BAND_PLAYS) {
            log.warn("The public list of band {} stops at {} gigs — it has at least that many", band,
                    MOST_A_BAND_PLAYS);
        }
        Map<GigId, Map<Platform, Publication>> published = listPublications.publicationsOf(band,
                gigs.stream().map(Gig::id).collect(Collectors.toSet()));
        return gigs.stream()
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
