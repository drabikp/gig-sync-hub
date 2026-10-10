package sk.drabikp.bzscraper.catalog.adapter.in.rest;

import sk.drabikp.bzscraper.gig.domain.GigDraft;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.sync.domain.PlatformState;
import sk.drabikp.bzscraper.sync.domain.SyncTask;

import java.util.List;

/**
 * A gig as the pages see it, put together by {@link GigViews} — its form fields and, per platform,
 * whether it is there and what the sync is doing — for {@link GigMapper} to answer with.
 *
 * @param rev the gig's revision (an edit names it)
 */
record GigView(GigId id, String rev, boolean past, GigDraft gig, List<OnPlatform> platforms) {

    /** The gig on one platform: its state, the event there ({@code ref}, {@code url}) and the open task, if any. */
    record OnPlatform(Platform platform, PlatformState state, String ref, String url, SyncTask task) {
    }
}
