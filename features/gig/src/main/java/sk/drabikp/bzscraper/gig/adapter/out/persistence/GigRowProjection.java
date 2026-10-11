package sk.drabikp.bzscraper.gig.adapter.out.persistence;

import sk.drabikp.bzscraper.gig.domain.Country;

import java.time.LocalDateTime;

/**
 * What one row of the gig list SELECTS — the columns it shows and no others, read straight into this
 * record by the query (a projection, so no entity joins the session to be dirty-checked, and the long
 * texts stay in the table). Turned into the domain's {@code GigRow} by {@link GigPages}.
 */
record GigRowProjection(String id, LocalDateTime showAt, LocalDateTime startDateTime, String title, String venue,
                        String city, Country country, boolean cancelled) {
}
