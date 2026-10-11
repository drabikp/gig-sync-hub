package sk.drabikp.bzscraper.calendar.domain;

import org.jmolecules.ddd.annotation.Service;
import sk.drabikp.bzscraper.calendar.domain.event.CalendarEventKind;
import sk.drabikp.bzscraper.calendar.domain.event.CalendarEventStatus;

import sk.drabikp.bzscraper.gig.domain.GigId;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * The calendar page's views of the events, and its counts. "Needs a look" is what the user
 * should see first: unseen changes, the calendar disagreeing with a linked gig, recent or
 * upcoming gigs not in the catalog (unless cancelled), and upcoming events the rules couldn't
 * sort. "Recent" is the last {@value #RECENT_DAYS} days.
 */
@Service
public enum CalendarFilter {

    NEEDS_A_LOOK, MISSING, NOT_SURE, GIGS, NOT_GIGS, DECIDED, ALL;

    public static final int RECENT_DAYS = 60;

    /** The summary line's numbers. */
    public record Counts(long events, long gigs, long notSure, long missing, long changes, long differences,
                         long linkable, long decided) {
    }

    public Predicate<CalendarRow> on(LocalDate today) {
        return switch (this) {
            case NEEDS_A_LOOK -> r -> r.needsAttention() || (!r.removed()
                    && (recent(r, today) && r.missingFromCatalog()
                    && r.classification().status() != CalendarEventStatus.CANCELLED
                    || upcoming(r, today) && notSure(r)));
            case MISSING -> CalendarRow::missingFromCatalog;
            case NOT_SURE -> CalendarFilter::notSure;
            case GIGS -> r -> r.classification().kind() == CalendarEventKind.GIG;
            case NOT_GIGS -> r -> r.classification().kind() == CalendarEventKind.NOT_GIG;
            case DECIDED -> r -> r.classification().decidedByUser();
            case ALL -> r -> true;
        };
    }

    public static Counts count(List<CalendarRow> rows, LocalDate today) {
        return new Counts(
                count(rows, r -> !r.removed()),
                count(rows, r -> r.classification().kind() == CalendarEventKind.GIG && !r.removed()),
                count(rows, CalendarFilter::notSure),
                count(rows, r -> r.missingFromCatalog() && recent(r, today)),
                count(rows, r -> r.change() != null),
                count(rows, r -> !r.match().differences().isEmpty()),
                linkable(rows).size(),
                count(rows, r -> r.classification().decidedByUser()));
    }

    /**
     * The gig events that can be linked to the catalog's gig that day without asking: the only
     * gig that day, not linked to another event yet, and wanted by no other event (two events
     * for one gig: the user picks).
     */
    public static List<CalendarRow> linkable(List<CalendarRow> rows) {
        Set<GigId> taken = rows.stream()
                .filter(r -> r.match().state() == CatalogMatch.State.LINKED && r.match().gig() != null)
                .map(r -> r.match().gig().id())
                .collect(Collectors.toSet());
        List<CalendarRow> candidates = rows.stream()
                .filter(r -> r.missingFromCatalog() && r.match().gig() != null
                        && !taken.contains(r.match().gig().id()))
                .toList();
        Map<GigId, Long> claims = candidates.stream()
                .collect(Collectors.groupingBy(r -> r.match().gig().id(), Collectors.counting()));
        return candidates.stream().filter(r -> claims.get(r.match().gig().id()) == 1).toList();
    }

    private static boolean notSure(CalendarRow r) {
        return r.classification().kind() == CalendarEventKind.UNSURE && !r.removed();
    }

    private static boolean upcoming(CalendarRow r, LocalDate today) {
        return !r.draft().date().isBefore(today);
    }

    private static boolean recent(CalendarRow r, LocalDate today) {
        return !r.draft().date().isBefore(today.minusDays(RECENT_DAYS));
    }

    private static long count(List<CalendarRow> rows, Predicate<CalendarRow> test) {
        return rows.stream().filter(test).count();
    }
}
