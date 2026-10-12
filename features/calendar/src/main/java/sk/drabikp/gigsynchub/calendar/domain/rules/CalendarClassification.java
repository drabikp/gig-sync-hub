package sk.drabikp.gigsynchub.calendar.domain.rules;

import org.jmolecules.ddd.annotation.ValueObject;
import sk.drabikp.gigsynchub.calendar.domain.event.CalendarEvent;
import sk.drabikp.gigsynchub.calendar.domain.event.CalendarEventKind;
import sk.drabikp.gigsynchub.calendar.domain.event.CalendarEventStatus;

import java.util.List;

/**
 * What a calendar event is and why. {@code suggested} is what the band profile says;
 * {@code kind} is the final answer — the user's remembered verdict when there is one
 * ({@code decidedByUser}), otherwise the suggestion. {@code reasons} are the rules that
 * matched, with the weight each added to {@code score}.
 */
@ValueObject
public record CalendarClassification(CalendarEvent event, CalendarEventKind kind, CalendarEventKind suggested,
                                     boolean decidedByUser, CalendarEventStatus status, int score,
                                     List<Reason> reasons) {

    /**
     * Why the rules said what they said: which kind of evidence ({@link Why}), the word or number
     * it found, and the weight it adds. {@link #text()} says it in English; pages translate
     * {@code why} with {@code value}.
     */
    public record Reason(Why why, String value, int weight) {

        public static Reason of(Why why, int weight) {
            return new Reason(why, null, weight);
        }

        public String text() {
            return why.english.formatted(value);
        }
    }

    /** The kinds of evidence, each with its English wording ({@code %s}: the value). */
    public enum Why {
        TITLE_STARTS_WITH("title starts with \"%s\""),
        MEMBER("title starts with a member's name \"%s\""),
        TITLE_CONTAINS("title contains \"%s\""),
        NOTES_CONTAIN("notes contain \"%s\""),
        NOTES_LABEL("notes have \"%s:\""),
        REPEATING("repeating event"),
        DAYS_LONG("%s days long"),
        ALL_DAY_FREE("all-day, time shown as free"),
        TITLE_REPEATED("title used %s times"),
        TRAVEL_LEADS_TO("travel \"%s\" leads to it"),
        CATALOG_GIG_SAME_DAY("the catalog has a gig that day"),
        CANCELLED_IN_CALENDAR("cancelled in the calendar"),
        CANCELLED_TITLE("cancelled: title starts with \"%s\""),
        CANCELLED_NOTES("cancelled: notes contain \"%s\""),
        TENTATIVE_IN_CALENDAR("tentative in the calendar"),
        TENTATIVE_TITLE("tentative: title starts with \"%s\""),
        TENTATIVE_NOTES("tentative: notes contain \"%s\""),
        TENTATIVE_FIELD("tentative: \"%s\"");

        private final String english;

        Why(String english) {
            this.english = english;
        }
    }

    public CalendarClassification {
        reasons = List.copyOf(reasons);
    }
}
