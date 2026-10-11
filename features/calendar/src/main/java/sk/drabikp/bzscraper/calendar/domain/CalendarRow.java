package sk.drabikp.bzscraper.calendar.domain;

import org.jmolecules.ddd.annotation.ValueObject;
import sk.drabikp.bzscraper.calendar.domain.event.CalendarEvent;
import sk.drabikp.bzscraper.calendar.domain.event.CalendarEventKind;
import sk.drabikp.bzscraper.calendar.domain.rules.CalendarClassification;

/**
 * One calendar event on the calendar page: how it is classified, what it says about the
 * gig, where it stands against the catalog, and what changed since the user last looked
 * ({@code change}, null when nothing). {@code removed}: gone from the calendar.
 */
@ValueObject
public record CalendarRow(CalendarClassification classification, CalendarGigDraft draft, CatalogMatch match,
                          CalendarChange change, boolean removed) {

    public CalendarEvent event() {
        return classification.event();
    }

    public String eventId() {
        return classification.event().id();
    }

    /** A gig that is in the calendar but not linked to the catalog. */
    public boolean missingFromCatalog() {
        return classification.kind() == CalendarEventKind.GIG && !removed
                && match.state() != CatalogMatch.State.LINKED;
    }

    /** The user has something to look at: an unseen change, or the calendar disagreeing with a linked gig. */
    public boolean needsAttention() {
        return change != null || !match.differences().isEmpty();
    }
}
