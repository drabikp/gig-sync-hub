package sk.drabikp.gigsynchub.calendar.domain;

import org.jmolecules.ddd.annotation.ValueObject;
import sk.drabikp.gigsynchub.gig.domain.Country;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * What a calendar event says about the gig, as far as it can be read: the start of a gig
 * form, never a finished {@link Gig} — the user checks it. {@code showTime} comes from the
 * notes' show-time label ({@link RuleKind#SHOWTIME_LABEL}) and is null when there is none;
 * {@code date} is the show's day (a show after midnight is on the next day). {@code venue},
 * {@code city} and {@code country} are read from the event's place and are null when it
 * doesn't say; {@code street} and {@code postalCode} likewise (they tell same-named towns
 * apart). {@code eventStart} is the event's own start time (usually the arrival), null
 * for all-day events.
 */
@ValueObject
public record CalendarGigDraft(String eventId, String title, LocalDate date, LocalTime showTime,
                               String venue, String city, Country country, String street, String postalCode,
                               LocalTime eventStart) {
}
