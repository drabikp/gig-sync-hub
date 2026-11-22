package sk.drabikp.gigsynchub.bandzone.portal.http;

import org.springframework.web.util.UriComponentsBuilder;
import sk.drabikp.gigsynchub.bandzone.BandzonePortalException;
import sk.drabikp.gigsynchub.bandzone.portal.BandzonePageData;
import sk.drabikp.gigsynchub.bandzone.portal.BandzoneTowns;
import sk.drabikp.gigsynchub.gig.domain.Admission;
import sk.drabikp.gigsynchub.gig.domain.Location;
import tools.jackson.databind.JsonNode;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the new-concert wizard and the concert's edit form send about a concert, as their own code builds
 * it: where (the town picked from Bandzone's city search, the club from its venue search or typed), when
 * (a date and a time in the wizard, {@link BandzonePageData#format one value} in the edit form) and the entry (its tickets).
 */
final class ConcertForm {

    static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    /**
     * What a concert without a venue says where: Bandzone wants a club or a typed place since its redesign,
     * and "-" is what the platform check already reads as no venue at all.
     */
    static final String NO_VENUE = "-";
    /** The label the form gives a ticket row of its own accord ("Základní", basic). */
    private static final String BASIC = "Základní";
    private static final Pattern PRICE = Pattern.compile(
            "\\s*(\\d+(?:[.,]\\d{1,2})?)\\s*(kč|czk|,-|€|eur|zł|pln|\\$|usd)?\\s*", Pattern.CASE_INSENSITIVE);

    /** The concert's place: a town of Bandzone's, and its club ({@code venueId}) or a typed venue. */
    record Place(long cityId, Long venueId, String venueName) {

        boolean club() {
            return venueId != null;
        }
    }

    private final BandzoneWeb web;

    ConcertForm(BandzoneWeb web) {
        this.web = web;
    }

    /**
     * The gig's town through Bandzone's city search: the suggestion that fits by name, country and district
     * ({@link BandzoneTowns}); several fits or none → the user corrects the town, never a guess. The club:
     * one in that town whose name matches exactly (ignoring case) is linked, anything else is typed, which
     * Bandzone accepts; no venue is {@link #NO_VENUE}.
     */
    Place place(Location location) throws BandzonePortalException {
        JsonNode towns = web.send("GET", UriComponentsBuilder.fromPath("/api/gig/search-cities")
                .queryParam("query", location.city()).queryParam("limit", 20).build().encode().toUriString(), null,
                "search its towns for '" + location.city() + "'");
        List<BandzoneTowns.Suggestion> suggestions = new ArrayList<>();
        for (int i = 0; i < towns.size(); i++) {
            JsonNode town = towns.get(i);
            suggestions.add(new BandzoneTowns.Suggestion(i, town.path("name").asString(""), description(town)));
        }
        BandzoneTowns.Choice choice = BandzoneTowns.choose(location.city(), location.country(), location.district(),
                suggestions);
        if (choice.pick() == null) {
            throw BandzonePortalException.needsUser(choice.problem());
        }
        long cityId = towns.get(choice.pick().index()).path("cityId").asLong();

        String venue = location.venue();
        if (venue == null || venue.isBlank()) {
            return new Place(cityId, null, NO_VENUE);
        }
        JsonNode clubs = web.send("GET", UriComponentsBuilder.fromPath("/api/gig/search-venues")
                .queryParam("cityId", cityId).queryParam("query", venue).build().encode().toUriString(), null,
                "search its clubs for '" + venue + "'");
        for (JsonNode club : clubs) {
            if (club.path("cityId").asLong() == cityId && venue.strip().equalsIgnoreCase(club.path("name").asString("").strip())) {
                return new Place(cityId, club.path("venueId").asLong(), club.path("name").asString());
            }
        }
        return new Place(cityId, null, venue.strip());
    }

    /**
     * A town as the search describes it, "okres Přerov, Olomoucký, Česká Republika" — the district written
     * as the place search writes it, the country as {@link BandzoneTowns} reads it.
     */
    static String description(JsonNode town) {
        List<String> parts = new ArrayList<>();
        String district = town.path("districtName").asString("");
        if (!district.isBlank()) {
            parts.add("okres " + district);
        }
        for (String part : List.of(town.path("regionName").asString(""), town.path("countryName").asString(""))) {
            if (!part.isBlank()) {
                parts.add(part);
            }
        }
        return String.join(", ", parts);
    }

    /** The concert's place as the edit form saves it: the club, or the typed venue with its town. */
    static void placeInto(Map<String, Object> patch, Place place) {
        if (place.club()) {
            patch.put("venueId", place.venueId());
            patch.put("venueText", null);
        } else {
            patch.put("venueId", null);
            patch.put("venueText", place.venueName());
            patch.put("cityId", place.cityId());
        }
    }

    /**
     * The entry as the form's tickets: free, voluntary, or paid — one basic ticket at the gig's price
     * (a number with its currency when it reads as one, else the text as written).
     */
    static Map<String, Object> tickets(Admission admission) {
        Map<String, Object> tickets = new LinkedHashMap<>();
        tickets.put("kind", kind(admission));
        tickets.put("ticketUrl", null);
        List<Map<String, Object>> rows = new ArrayList<>();
        if (admission.isPaid()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("label", BASIC);
            Matcher price = PRICE.matcher(admission.amount());
            if (price.matches()) {
                row.put("price", price.group(1).replace(',', '.'));
                row.put("priceText", null);
                row.put("currency", currency(price.group(2)));
            } else {
                row.put("price", null);
                row.put("priceText", admission.amount().strip());
                row.put("currency", "CZK");
            }
            row.put("validUntil", null);
            row.put("soldOut", false);
            row.put("note", null);
            row.put("url", null);
            rows.add(row);
        }
        tickets.put("rows", rows);
        return tickets;
    }

    /** The tickets' {@code kind} Bandzone stores for an admission. */
    static String kind(Admission admission) {
        return switch (admission.type()) {
            case PAID -> "paid";
            case VOLUNTARY -> "voluntary";
            case FREE -> "free";
        };
    }

    static String date(ZonedDateTime when) {
        return when.withZoneSameInstant(BandzonePageData.PRAGUE).format(DATE);
    }

    static String time(ZonedDateTime when) {
        return when.withZoneSameInstant(BandzonePageData.PRAGUE).format(TIME);
    }

    private static String currency(String written) {
        if (written == null) {
            return "CZK";
        }
        return switch (written.toLowerCase(Locale.ROOT)) {
            case "€", "eur" -> "EUR";
            case "zł", "pln" -> "PLN";
            case "$", "usd" -> "USD";
            default -> "CZK";
        };
    }
}
