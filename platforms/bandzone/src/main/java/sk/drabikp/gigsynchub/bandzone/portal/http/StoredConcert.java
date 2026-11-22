package sk.drabikp.gigsynchub.bandzone.portal.http;

import sk.drabikp.gigsynchub.bandzone.portal.BandzonePageData;
import tools.jackson.databind.JsonNode;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A concert as Bandzone stored it, read from its edit page ({@code /koncert/<id>/upravit}), which carries
 * the whole of it as data ({@link BandzonePageData}): what was saved, the lineup — bands with a profile by
 * {@code bandId}, the rest as the concert's own guests by {@code guestId} — and its state.
 */
record StoredConcert(long id, String title, String description, ZonedDateTime start, ZonedDateTime end,
                     Long cityId, Long venueId, String venueText, String facebookUrl, int statusId,
                     List<Band> bands, List<Guest> guests) {

    /** Bandzone's {@code statusId} of a cancelled concert (2 public, 4 postponed, 6 draft, 7 played). */
    static final int CANCELLED = 3;
    /** A concert anyone can see. */
    static final int PUBLIC = 2;
    /** A concert already played: archived, its state no longer changes. */
    static final int PLAYED = 7;

    /** A performing band with a Bandzone profile. */
    record Band(long bandId, String name) {
    }

    /** A performer without a profile: the concert's own entry. */
    record Guest(long guestId, String name) {
    }

    static StoredConcert of(JsonNode gig) {
        List<Band> bands = new ArrayList<>();
        for (JsonNode band : gig.path("lineup")) {
            bands.add(new Band(band.path("bandId").asLong(), band.path("name").asString("")));
        }
        List<Guest> guests = new ArrayList<>();
        for (JsonNode guest : gig.path("guests")) {
            guests.add(new Guest(guest.path("guestId").asLong(), guest.path("name").asString("")));
        }
        return new StoredConcert(gig.path("gigId").asLong(), BandzonePageData.text(gig, "title"),
                BandzonePageData.text(gig, "description"), BandzonePageData.dateTime(gig, "dateFrom"),
                BandzonePageData.dateTime(gig, "dateUntil"), number(gig, "cityId"), number(gig, "venueId"),
                BandzonePageData.text(gig, "venueText"), BandzonePageData.text(gig, "facebookUrl"),
                gig.path("statusId").asInt(), List.copyOf(bands), List.copyOf(guests));
    }

    boolean cancelled() {
        return statusId == CANCELLED;
    }

    /** Every performer's name, bands and guests alike, in that order. */
    List<String> performers() {
        List<String> names = new ArrayList<>();
        bands.forEach(band -> names.add(band.name()));
        guests.forEach(guest -> names.add(guest.name()));
        return names;
    }

    private static Long number(JsonNode gig, String field) {
        JsonNode value = gig.get(field);
        return value == null || value.isNull() ? null : value.asLong();
    }
}
