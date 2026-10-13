package sk.drabikp.gigsynchub.gig.domain;

import org.jmolecules.ddd.annotation.ValueObject;

import java.util.List;

/**
 * One page of the gig list: the rows, where to carry on from, and how many gigs the filter matches
 * in all (so the page can say "50 of 213" without reading the other 163).
 *
 * <p>{@code next} is null when the rows ran out — the band has reached the end of what it asked for.
 *
 * <p>{@code total} is null on every page but the FIRST. Counting is a second query over everything
 * the filter matches, and the answer cannot change what the page says: it already has the number
 * from the page it started with, and "Show more" adds rows to that same list. So a band walking a
 * long catalog used to pay for a full count per click, to be told again what it was told at the
 * start. The count travels with the page that needs it and the rest say nothing.
 */
@ValueObject
public record GigSlice(List<GigRow> rows, GigCursor next, Long total) {

    public GigSlice {
        rows = rows == null ? List.of() : List.copyOf(rows);
    }

    public static GigSlice empty() {
        return new GigSlice(List.of(), null, 0L);
    }

    public boolean hasMore() {
        return next != null;
    }
}
