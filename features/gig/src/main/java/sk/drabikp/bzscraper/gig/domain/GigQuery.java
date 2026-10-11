package sk.drabikp.bzscraper.gig.domain;

import java.util.Set;

/**
 * What a page of the gig list asks for: which side of today, matching which words, narrowed to or away
 * from which gigs, cancelled or not, carrying on from where, and how many rows. The store answers it
 * with a {@link GigSlice} — the filtering, the ordering and the paging all happen there, never in the
 * page that shows it.
 *
 * <p>{@code text} is folded on the way in ({@link TextFold}), because that is how it is stored and
 * compared. {@code onlyIds} and {@code exceptIds} are how a filter the gig table knows nothing about —
 * a platform state — narrows the list: both {@code null} restrict nothing, and an EMPTY
 * {@code onlyIds} matches no gig at all (never "everything", which would show a band the opposite of
 * what it asked for). Which way round depends on which side is ENUMERABLE: the gigs a platform failed
 * on are few and name themselves; the gigs missing from a platform are "all but these".
 */
public record GigQuery(Period period, String text, Set<GigId> onlyIds, Set<GigId> exceptIds, Boolean cancelled,
                       GigCursor after, int size) {

    /** Which side of the band's show day a page reads. */
    public enum Period { UPCOMING, PAST }

    public static final int DEFAULT_SIZE = 50;
    public static final int MAX_SIZE = 200;

    public GigQuery {
        period = period == null ? Period.UPCOMING : period;
        text = TextFold.fold(text);
        onlyIds = onlyIds == null ? null : Set.copyOf(onlyIds);
        exceptIds = exceptIds == null ? null : Set.copyOf(exceptIds);
        size = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    }

    /** The first page of what is to come. */
    public static GigQuery upcoming() {
        return new GigQuery(Period.UPCOMING, "", null, null, null, null, DEFAULT_SIZE);
    }

    public GigQuery of(Period wanted) {
        return new GigQuery(wanted, text, onlyIds, exceptIds, cancelled, after, size);
    }

    public GigQuery matching(String words) {
        return new GigQuery(period, words, onlyIds, exceptIds, cancelled, after, size);
    }

    /** Narrowed to these gigs, and to nothing when the set is empty. */
    public GigQuery onlyIn(Set<GigId> ids) {
        return new GigQuery(period, text, ids, exceptIds, cancelled, after, size);
    }

    /** Everything but these gigs (an empty set leaves every gig in). */
    public GigQuery excluding(Set<GigId> ids) {
        return new GigQuery(period, text, onlyIds, ids, cancelled, after, size);
    }

    /** Only the gigs still on, or only the cancelled ones. */
    public GigQuery cancelled(Boolean wanted) {
        return new GigQuery(period, text, onlyIds, exceptIds, wanted, after, size);
    }

    public GigQuery from(GigCursor cursor) {
        return new GigQuery(period, text, onlyIds, exceptIds, cancelled, cursor, size);
    }

    public GigQuery sized(int rows) {
        return new GigQuery(period, text, onlyIds, exceptIds, cancelled, after, rows);
    }

    public boolean searching() {
        return !text.isBlank();
    }

    public boolean excludingAny() {
        return exceptIds != null && !exceptIds.isEmpty();
    }

    /** A narrowing that nothing can satisfy — the store answers an empty slice without asking. */
    public boolean matchesNothing() {
        return onlyIds != null && onlyIds.isEmpty();
    }
}
