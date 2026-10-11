package sk.drabikp.bzscraper.gig.domain;

import org.jmolecules.ddd.annotation.ValueObject;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

/**
 * Which concert a copy of a gig is about: the day it starts and the venue, folded so that the
 * same place typed by a band and scraped from two platforms comes out the same. It was this app's
 * gig identity until 2026-10 and is no longer ({@link GigId} is); what it answers now is the
 * question nothing else can — a platform's event carries no id of ours, so Import and the nightly
 * check recognise a copy by the day and the place.
 *
 * <p>The venue is lowercased, trimmed and its inner whitespace collapsed. A gig whose venue is not
 * known yet ("TBA") is matched by its city instead ({@code "@" + city}), so two such gigs on one
 * day in different cities are still two concerts.
 *
 * <p>A band may not have two gigs with one match — same day, same venue is the same concert — and
 * the store holds that ({@code unique (band_id, match_key)}), which is why a gig moved onto
 * another's day and venue is refused rather than merged.
 */
@ValueObject
public record GigMatch(LocalDate date, String venue) {

    public GigMatch {
        Objects.requireNonNull(date, "the day a gig starts");
        Objects.requireNonNull(venue, "the venue a gig is matched by");
    }

    public static GigMatch of(Gig gig) {
        String venue = gig.location().venue();
        return new GigMatch(gig.schedule().startDate(),
                venue != null ? normalizeVenue(venue) : "@" + normalizeVenue(gig.location().city()));
    }

    /** The match as one text — how a row keeps it: {@code "2026-10-01|klub 007"}. */
    public String key() {
        return date + "|" + venue;
    }

    /** The match a {@link #key()} stands for. */
    public static GigMatch fromKey(String key) {
        int separator = key.indexOf('|');
        return new GigMatch(LocalDate.parse(key.substring(0, separator)), key.substring(separator + 1));
    }

    /** How to name the concert in a sentence: {@code "2026-10-01 · klub 007"}. */
    public String label() {
        return date + " · " + (venue.startsWith("@") ? venue.substring(1) : venue);
    }

    static String normalizeVenue(String venue) {
        if (venue == null) {
            return "";
        }
        return venue.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
