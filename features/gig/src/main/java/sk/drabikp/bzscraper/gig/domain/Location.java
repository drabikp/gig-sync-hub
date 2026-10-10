package sk.drabikp.bzscraper.gig.domain;

import java.time.ZoneId;

/**
 * Value object: where a gig happens. City is required; venue is optional (shown as
 * "TBA" until known); country is optional (null when unknown) and carries the
 * timezone/canonical name the platforms need. {@code address} (optional) is what tells
 * same-named towns apart — district, region, postal code, coordinates — when the town
 * was picked from the place search.
 */
public record Location(String venue, String city, Country country, Address address) {

    /** Where the times of a gig whose country is unknown are read: the band's home, not the server's zone. */
    public static final ZoneId UNKNOWN_COUNTRY_ZONE = ZoneId.of("Europe/Bratislava");

    public Location {
        if (city == null || city.isBlank()) {
            throw new IllegalArgumentException("gig city is required");
        }
        city = city.trim();
        venue = (venue == null || venue.isBlank() || venue.trim().equalsIgnoreCase("TBA")) ? null : venue.trim();
    }

    public Location(String venue, String city, Country country) {
        this(venue, city, country, null);
    }

    /** Venue for display/publishing; "TBA" when not yet known. */
    public String displayVenue() {
        return venue != null ? venue : "TBA";
    }

    /** The country's short English name, or empty when unknown. */
    public String countryName() {
        return country != null ? country.displayName() : "";
    }

    /** IANA timezone id, or empty when the country is unknown. */
    public String timezone() {
        return country != null ? country.timezone() : "";
    }

    /** The zone the gig's times are read in: its country's, else {@link #UNKNOWN_COUNTRY_ZONE}. */
    public static ZoneId zoneOf(Country country) {
        return country != null ? ZoneId.of(country.timezone()) : UNKNOWN_COUNTRY_ZONE;
    }

    /** The town's district ("okres Přerov"), when known. */
    public String district() {
        return address != null ? address.district() : null;
    }
}
