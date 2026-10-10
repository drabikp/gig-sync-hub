package sk.drabikp.bzscraper.gig.domain;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Locale;

/**
 * Identity of a {@link Gig} — its natural business key: start date + normalized
 * venue. Single-band tool, so no band component is needed. Venue is lowercased,
 * trimmed, and internal whitespace collapsed, so scraped/typed variants of the same
 * venue resolve to the same identity. Same date + clearly different venue = two
 * gigs (a double-header), not one. A gig whose venue is not known yet ("TBA") is
 * identified by its city instead ({@code "@" + city}), so two such gigs on one day in
 * different cities stay two gigs.
 */
public record GigId(LocalDate date, String venue) {

    public static GigId of(Gig gig) {
        String venue = gig.location().venue();
        return new GigId(gig.schedule().startDate(),
                venue != null ? normalizeVenue(venue) : "@" + normalizeVenue(gig.location().city()));
    }

    /** The identity as one text — how stored records refer to a gig: {@code "2026-10-01|klub 007"}. */
    public String key() {
        return date + "|" + venue;
    }

    /** The identity a {@link #key()} stands for. */
    public static GigId fromKey(String key) {
        int separator = key.indexOf('|');
        return new GigId(LocalDate.parse(key.substring(0, separator)), key.substring(separator + 1));
    }

    /** The identity as a URL-safe token, for addresses that name a gig ({@code /api/gigs/<token>}). */
    public String token() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(key().getBytes(StandardCharsets.UTF_8));
    }

    /** The identity a {@link #token()} stands for; {@link UnknownReferenceException} when it stands for none. */
    public static GigId fromToken(String token) {
        String key;
        try {
            key = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new UnknownReferenceException("not a gig: " + token, e);
        }
        if (key.indexOf('|') < 0) {
            throw new UnknownReferenceException("not a gig: " + token);
        }
        try {
            return fromKey(key);
        } catch (DateTimeException e) {
            throw new UnknownReferenceException("not a gig: " + token, e);
        }
    }

    static String normalizeVenue(String venue) {
        if (venue == null) {
            return "";
        }
        return venue.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
