package sk.drabikp.gigsynchub.gig.domain;

import org.jmolecules.ddd.annotation.ValueObject;

import java.util.List;

/**
 * One page of the gig list: the rows, where to carry on from, and how many gigs the filter matches
 * in all (so the page can say "50 of 213" without reading the other 163).
 *
 * <p>{@code next} is null when the rows ran out — the band has reached the end of what it asked for.
 */
@ValueObject
public record GigSlice(List<GigRow> rows, GigCursor next, long total) {

    public GigSlice {
        rows = rows == null ? List.of() : List.copyOf(rows);
    }

    public static GigSlice empty() {
        return new GigSlice(List.of(), null, 0);
    }

    public boolean hasMore() {
        return next != null;
    }
}
