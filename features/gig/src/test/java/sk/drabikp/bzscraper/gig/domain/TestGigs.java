package sk.drabikp.bzscraper.gig.domain;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/** Test fixtures for building valid {@link Gig} aggregates concisely. */
public final class TestGigs {

    private TestGigs() {
    }

    /**
     * The identity of a gig named like this. A gig's identity is its own and random in the app, but a
     * test that builds "the same gig" twice means the same gig both times — it used to get that for
     * free, when identity was the day and the venue. So here it is made from what the fixture was
     * asked for, and {@code gig("Eufory", "Klub 007")} is one gig however often it is built.
     */
    public static GigId id(String fixture) {
        return new GigId(UUID.nameUUIDFromBytes(fixture.getBytes(StandardCharsets.UTF_8)));
    }

    public static Gig gig(String title, String venue) {
        return gig(title, venue, ZonedDateTime.of(2026, 9, 15, 20, 0, 0, 0, ZoneId.of("Europe/Prague")));
    }

    public static Gig gig(String title, String venue, ZonedDateTime start) {
        return gig(title, venue, "Praha", start);
    }

    /** A gig in a named town — what the list's search and its order are tried against. */
    public static Gig gig(String title, String venue, String city, ZonedDateTime start) {
        return Gig.create(id(title + "|" + venue + "|" + city + "|" + start), title, GigSchedule.startingAt(start),
                new Location(venue, city, Country.CZECHIA), List.of(), Admission.free(),
                null, null, null, null);
    }
}
