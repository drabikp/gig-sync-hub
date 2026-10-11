package sk.drabikp.bzscraper.catalog.adapter.in.rest;

import sk.drabikp.bzscraper.gig.domain.GigRow;

import java.util.List;

/**
 * A LIST row as the page gets it: the row the store read, whether the band has played it, and where
 * it stands on each platform. Less than a {@link GigView} on purpose — a list shows a line per gig,
 * and the gig's own page reads the rest when it is opened.
 */
record GigRowView(GigRow row, boolean past, List<GigView.OnPlatform> platforms) {
}
