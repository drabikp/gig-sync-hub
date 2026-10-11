package sk.drabikp.bzscraper.gig.domain;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/** Test fixtures for building valid {@link Gig} aggregates concisely. */
public final class TestGigs {

    private TestGigs() {
    }

    public static Gig gig(String title, String venue) {
        return gig(title, venue, ZonedDateTime.of(2026, 9, 15, 20, 0, 0, 0, ZoneId.of("Europe/Prague")));
    }

    public static Gig gig(String title, String venue, ZonedDateTime start) {
        return gig(title, venue, "Praha", start);
    }

    /** A gig in a named town — what the list's search and its order are tried against. */
    public static Gig gig(String title, String venue, String city, ZonedDateTime start) {
        return Gig.create(title, GigSchedule.startingAt(start),
                new Location(venue, city, Country.CZECHIA), List.of(), Admission.free(),
                null, null, null, null);
    }
}
