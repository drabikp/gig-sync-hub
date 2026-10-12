package sk.drabikp.gigsynchub.catalog.adapter.in.rest;

import org.springframework.stereotype.Component;
import sk.drabikp.gigsynchub.catalog.adapter.in.rest.api.GigDetailResponse;
import sk.drabikp.gigsynchub.catalog.adapter.in.rest.api.GigEditedResponse;
import sk.drabikp.gigsynchub.catalog.adapter.in.rest.api.GigListResponse;
import sk.drabikp.gigsynchub.catalog.adapter.in.rest.api.GigResponse;
import sk.drabikp.gigsynchub.catalog.application.port.in.ListGigsUseCase;
import sk.drabikp.gigsynchub.catalog.application.port.in.ListPublicationsUseCase;
import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.gig.domain.Gig;
import sk.drabikp.gigsynchub.gig.domain.GigDraft;
import sk.drabikp.gigsynchub.gig.domain.GigId;
import sk.drabikp.gigsynchub.gig.domain.GigQuery;
import sk.drabikp.gigsynchub.gig.domain.GigRow;
import sk.drabikp.gigsynchub.gig.domain.GigSlice;
import sk.drabikp.gigsynchub.gig.domain.platform.Platform;
import sk.drabikp.gigsynchub.gig.domain.platform.Platforms;
import sk.drabikp.gigsynchub.gig.domain.platform.Publication;
import sk.drabikp.gigsynchub.sync.application.port.in.SyncLogUseCase;
import sk.drabikp.gigsynchub.sync.domain.PlatformState;
import sk.drabikp.gigsynchub.sync.domain.QueueResult;
import sk.drabikp.gigsynchub.sync.domain.SyncStatus;
import sk.drabikp.gigsynchub.sync.domain.SyncTask;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

    /**
     * One page of the band's list. The store does the filtering, the ordering and the paging; this
     * adds each ROW's platform states, read for the page's gigs rather than the band's whole catalog.
     *
     * <p>The two filters the gig table knows nothing about are turned into the sets it does
     * understand first: the gigs a platform FAILED on name themselves (failed work is few), while
     * "missing from some platform" is every gig BUT those already everywhere — so one narrows and the
     * other excludes. Neither is read unless the band asks for it.
     */
    GigListResponse list(BandId band, GigQuery asked, Show show) {
        GigSlice slice = gigs.page(band, narrowed(band, asked, show));
        Set<GigId> onThisPage = slice.rows().stream().map(GigRow::id).collect(Collectors.toSet());
        Map<GigId, Map<Platform, Publication>> published = publications.publicationsOf(band, onThisPage);
        Map<GigId, Map<Platform, SyncTask>> open = latestUnfinished(band);
        return mapper.toResponse(slice, slice.rows().stream()
                .map(row -> rowView(row, published.getOrDefault(row.id(), Map.of()),
                        open.getOrDefault(row.id(), Map.of())))
                .toList());
    }

    /** Which gigs a list shows: everything, the ones waiting for the user, or the ones not out yet. */
    enum Show { ALL, NEEDS, PUBLISHABLE }

    private GigQuery narrowed(BandId band, GigQuery asked, Show show) {
        return switch (show) {
            case ALL -> asked;
            case NEEDS -> asked.onlyIn(syncLog.unfinished(band).stream()
                    .filter(task -> task.status() == SyncStatus.FAILED)
                    .map(SyncTask::gigId).collect(Collectors.toSet()));
            case PUBLISHABLE -> asked.excluding(publishedEverywhere(band)).cancelled(false);
        };
    }

    /** The gigs that are on every platform there is — what "publishable" leaves out. */
    private Set<GigId> publishedEverywhere(BandId band) {
        int everywhere = platforms.all().size();
        return publications.publicationsByGig(band).entrySet().stream()
                .filter(gig -> gig.getValue().size() >= everywhere)
                .map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    private GigRowView rowView(GigRow row, Map<Platform, Publication> published, Map<Platform, SyncTask> open) {
        return new GigRowView(row, row.showAt().toLocalDate().isBefore(LocalDate.now(clock)),
                platforms.all().stream()
                        .map(platform -> onPlatform(platform, published.get(platform), open.get(platform)))
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
        // this gig's publications, not the band's — one gig's page has no business reading the catalog's
        return view(gig, publications.publicationsOf(band, Set.of(gig.id())).getOrDefault(gig.id(), Map.of()),
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
