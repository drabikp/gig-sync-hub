package sk.drabikp.bzscraper.calendar.domain.rules;

import org.jmolecules.ddd.annotation.ValueObject;

/**
 * The kinds of rule a band profile is made of. The code knows only these kinds; which
 * words, labels and weights a band uses is data ({@link ProfileRule}). Text values may
 * list alternatives separated by {@code |} and are compared without accents or case.
 *
 * <p>Weighted kinds add their weight to an event's score when they match. Role kinds have
 * no weight: they mark travel events or decide a gig's status.
 */
@ValueObject
public enum RuleKind {

    /** The title (ignoring leading punctuation) starts with one of the words. */
    TITLE_STARTS_WITH(true, Value.TEXT),
    /** The title contains one of the texts. */
    TITLE_CONTAINS(true, Value.TEXT),
    /** The title starts with a band member's name (personal events, absences). */
    MEMBER(true, Value.TEXT),
    /** The notes have one of the labels, written {@code label:} (a day sheet: "Soundcheck:"). */
    NOTES_LABEL(true, Value.TEXT),
    /** The notes contain one of the texts. */
    NOTES_CONTAIN(true, Value.TEXT),
    /** A repeating event (weekly call, anniversary). */
    REPEATING(true, Value.NONE),
    /** Longer than the given number of days (holidays, trips). */
    LONGER_THAN_DAYS(true, Value.NUMBER),
    /** An all-day event that leaves the time shown as free (absences, reminders). */
    ALL_DAY_FREE(true, Value.NONE),
    /** The same title is used at least the given number of times (gig titles are unique). */
    TITLE_REPEATED(true, Value.NUMBER),
    /** The catalog already has a gig that day. */
    CATALOG_GIG_SAME_DAY(true, Value.NONE),
    /** A travel event that day goes to the same place or arrives within an hour of the start. */
    TRAVEL_LEADS_TO(true, Value.NONE),

    /** Titles starting with one of the words are travel (used by {@link #TRAVEL_LEADS_TO}). */
    TRAVEL(false, Value.TEXT),
    /** A title starting with one of the words means cancelled ("ZRUŠENÉ …"). */
    CANCELLED_TITLE(false, Value.TEXT),
    /** Notes containing one of the texts mean cancelled. */
    CANCELLED_NOTES(false, Value.TEXT),
    /** A title starting with one of the words means tentative ("PREDBEŽNE: …", "???"). */
    TENTATIVE_TITLE(false, Value.TEXT),
    /** Notes containing one of the texts mean tentative ("v jednaní"). */
    TENTATIVE_NOTES(false, Value.TEXT),
    /**
     * {@code label=prefix}: a notes field {@code label:} whose value does not start with
     * {@code prefix} means tentative ({@code stav=potvrd}: "Stav: Potvrdené" is confirmed,
     * any other "Stav:" is not).
     */
    CONFIRMED_FIELD(false, Value.FIELD),
    /**
     * The notes label whose time is the show time ("Showtime: 19:30"), for turning an event
     * into a gig — the event's own start is usually the arrival. The first label found wins.
     */
    SHOWTIME_LABEL(false, Value.TEXT);

    /** What a rule of this kind needs as its value. */
    public enum Value { NONE, TEXT, NUMBER, FIELD }

    private final boolean weighted;
    private final Value value;

    RuleKind(boolean weighted, Value value) {
        this.weighted = weighted;
        this.value = value;
    }

    public boolean weighted() {
        return weighted;
    }

    public Value value() {
        return value;
    }
}
