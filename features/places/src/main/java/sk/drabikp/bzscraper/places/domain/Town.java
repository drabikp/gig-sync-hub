package sk.drabikp.bzscraper.places.domain;

import sk.drabikp.bzscraper.gig.domain.Address;
import sk.drabikp.bzscraper.gig.domain.Coordinates;
import sk.drabikp.bzscraper.gig.domain.Country;
import sk.drabikp.bzscraper.gig.domain.Location;

/**
 * A town as a place search knows it — not just a name: town names repeat a lot (three
 * Hranice, two Czech villages and a Slovak city called Košice), so the district
 * ("okres Přerov") and region tell them apart. {@code postalCode} may be null;
 * {@code coordinates} (the town's centre) is null for a town only typed.
 */
public record Town(String name, String district, String region, String postalCode, Country country,
                   Coordinates coordinates) {

    public Town {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("town name is required");
        }
        name = name.strip();
    }

    /** A town only typed, not picked from the place search: nothing but its name (and country). */
    public static Town typed(String name, Country country) {
        return new Town(name, null, null, null, country, null);
    }

    /** The town was picked from the place search: its district and coordinates are known. */
    public boolean resolved() {
        return district != null && coordinates != null;
    }

    /** The town a gig's location names (resolved when its address was). */
    public static Town of(Location location) {
        Address a = location.address();
        return a != null && a.resolved()
                ? new Town(location.city(), a.district(), a.region(), a.postalCode(), location.country(),
                a.coordinates().orElseThrow())
                : typed(location.city(), location.country());
    }

    /** The gig's address in this town ({@code street}, {@code postalCodeOverride} as typed, may be null). */
    public Address address(String street, String postalCodeOverride) {
        String code = postalCodeOverride != null && !postalCodeOverride.isBlank() ? postalCodeOverride : postalCode;
        return resolved()
                ? new Address(street, code, district, region, coordinates.latitude(), coordinates.longitude())
                : new Address(street, code, null, null, null, null);
    }
}
