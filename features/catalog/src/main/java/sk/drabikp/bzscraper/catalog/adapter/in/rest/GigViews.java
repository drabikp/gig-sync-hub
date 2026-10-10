package sk.drabikp.bzscraper.catalog.adapter.in.rest;

import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.catalog.adapter.in.rest.api.GigDetailResponse;
import sk.drabikp.bzscraper.catalog.adapter.in.rest.api.GigEditedResponse;
import sk.drabikp.bzscraper.catalog.adapter.in.rest.api.GigResponse;
import sk.drabikp.bzscraper.catalog.application.port.in.ListGigsUseCase;
import sk.drabikp.bzscraper.catalog.application.port.in.ListPublicationsUseCase;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.GigDraft;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.gig.domain.platform.Platforms;
import sk.drabikp.bzscraper.gig.domain.platform.Publication;
import sk.drabikp.bzscraper.sync.application.port.in.SyncLogUseCase;
import sk.drabikp.bzscraper.sync.domain.PlatformState;
import sk.drabikp.bzscraper.sync.domain.QueueResult;
import sk.drabikp.bzscraper.sync.domain.SyncTask;

import java.time.Clock;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gigs as the pages get them: the gig's form fields and, per platform, whether it is there and
 * what the sync is doing with it — the catalog, its publications and the open sync work put
 * together into {@link GigView}s, answered through {@link GigMapper}.
 */
@Component
class GigViews {

    /** How much sync history a gig's page shows. */
    private static final int HISTORY = 500;

    private final ListGigsUseCase gigs;
    private final ListPublicationsUseCase publications;
    private final SyncLogUseCase syncLog;
    private final Platforms platforms;
    private final Clock clock;
    private final GigMapper mapper;

    GigViews(ListGigsUseCase gigs, ListPublicationsUseCase publications, SyncLogUseCase syncLog, Platforms platforms,
             Clock clock, GigMapper mapper) {
        this.gigs = gigs;
        this.publications = publications;
        this.syncLog = syncLog;
        this.platforms = platforms;
        this.clock = clock;
        this.mapper = mapper;
    }

    /** Every gig of the band, earliest first. */
    List<GigResponse> all(BandId band) {
        Map<GigId, Map<Platform, Publication>> published = publications.publicationsByGig(band);
        Map<GigId, Map<Platform, SyncTask>> open = latestUnfinished(band);
        return mapper.toResponse(gigs.allGigs(band).stream()
                .sorted(Comparator.comparing(g -> g.schedule().start()))
                .map(g -> view(g, published.getOrDefault(g.id(), Map.of()), open.getOrDefault(g.id(), Map.of())))
                .toList());
    }

    GigResponse of(BandId band, Gig gig) {
        return mapper.toResponse(view(band, gig));
    }

    /** The gig with its sync history, newest first. */
    GigDetailResponse detail(BandId band, Gig gig) {
        return mapper.toResponse(view(band, gig), syncLog.history(band, gig.id(), HISTORY));
    }

    /** The gig as edited, and the work the edit queued. */
    GigEditedResponse edited(BandId band, Gig gig, QueueResult queued) {
        return mapper.toResponse(view(band, gig), queued);
    }

    /** Per gig and platform, the newest work not finished yet: queued, running, or failed and waiting for the user. */
    private Map<GigId, Map<Platform, SyncTask>> latestUnfinished(BandId band) {
        Map<GigId, Map<Platform, SyncTask>> latest = new HashMap<>();
        for (SyncTask task : syncLog.unfinished(band)) {
            latest.computeIfAbsent(task.gigId(), id -> new HashMap<>())
                    .merge(task.platform(), task, (a, b) -> a.id() > b.id() ? a : b);
        }
        return latest;
    }

    private GigView view(BandId band, Gig gig) {
        return view(gig, publications.publicationsByGig(band).getOrDefault(gig.id(), Map.of()),
                latestUnfinished(band).getOrDefault(gig.id(), Map.of()));
    }

    private GigView view(Gig gig, Map<Platform, Publication> published, Map<Platform, SyncTask> open) {
        return new GigView(gig.id(), GigRevisions.of(gig), gig.isPast(clock), GigDraft.of(gig),
                platforms.all().stream()
                        .map(platform -> onPlatform(platform, published.get(platform), open.get(platform)))
                        .toList());
    }

    /** Where the gig stands there ({@link PlatformState}), its event there and the work under way. */
    private GigView.OnPlatform onPlatform(Platform platform, Publication publication, SyncTask task) {
        String ref = publication != null && publication.hasExternalRef() ? publication.externalRef() : null;
        String url = ref != null ? platforms.traits(platform).eventUrl(ref) : null;
        return new GigView.OnPlatform(platform, PlatformState.of(publication != null, task), ref, url, task);
    }
}
