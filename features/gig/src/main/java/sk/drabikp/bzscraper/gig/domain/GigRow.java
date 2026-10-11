package sk.drabikp.bzscraper.gig.domain;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

/**
 * A gig as a LIST row needs it, which is a handful of columns rather than the whole gig: the store
 * reads these and no more, so a page costs neither the long texts (description, lineup) nor a
 * managed entity per row that the session would then have to dirty-check.
 *
 * <p>{@code showAt} is when the band plays in its own wall clock — what the list is ordered and
 * paged by, and what the cursor carries. {@code start} is the event's start, as the row shows it.
 */
public record GigRow(GigId id, LocalDateTime showAt, ZonedDateTime start, String title, String venue,
                     String city, Country country, boolean cancelled) {

    public GigRow {
        if (id == null || showAt == null || start == null) {
            throw new IllegalArgumentException("a list row needs its gig, its show time and its start");
        }
    }
}
