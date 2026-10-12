package sk.drabikp.gigsynchub.gig.adapter.out.persistence;

import sk.drabikp.gigsynchub.gig.domain.Country;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * What one row of the gig list SELECTS — the columns it shows and no others, read straight into this
 * record by the query (a projection, so no entity joins the session to be dirty-checked, and the long
 * texts stay in the table). Turned into the domain's {@code GigRow} by {@link GigPages}.
 */
record GigRowProjection(UUID id, LocalDateTime showAt, LocalDateTime startDateTime, String title, String venue,
                        String city, Country country, boolean cancelled) {
}
