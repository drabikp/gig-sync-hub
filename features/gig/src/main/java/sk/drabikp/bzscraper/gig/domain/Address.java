package sk.drabikp.bzscraper.gig.domain;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Value object: where exactly a gig is, beyond the town's name — what tells the platforms
 * which of several same-named towns is meant. Every part is optional; a town picked from
 * the place search gives district, region and coordinates (the town's centre).
 */
public record Address(String street, String postalCode, String district, String region,
                      Double latitude, Double longitude) {

    /** No details at all (a typed town); a {@link Location} keeps null instead. */
    public static final Address NONE = new Address(null, null, null, null, null, null);

    public Address {
        street = blankToNull(street);
        postalCode = blankToNull(postalCode);
        district = blankToNull(district);
        region = blankToNull(region);
        if ((latitude == null) != (longitude == null)) {
            throw new IllegalArgumentException("coordinates need both latitude and longitude");
        }
    }

    /** The address of these parts, or null when none of them is there. */
    public static Address ofParts(String street, String postalCode, String district, String region,
                                  Double latitude, Double longitude) {
        Address address = new Address(street, postalCode, district, region, latitude, longitude);
        return address.equals(NONE) ? null : address;
    }

    /** The town was picked from the place search (not just typed). */
    public boolean resolved() {
        return district != null && latitude != null;
    }

    /** The town's centre, when known. */
    public Optional<Coordinates> coordinates() {
        return latitude == null ? Optional.empty() : Optional.of(new Coordinates(latitude, longitude));
    }

    /** Great-circle distance in km to the point; empty without coordinates. */
    public OptionalDouble kilometresTo(Coordinates point) {
        return coordinates().map(c -> OptionalDouble.of(c.kilometresTo(point))).orElse(OptionalDouble.empty());
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
