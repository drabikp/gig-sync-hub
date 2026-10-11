package sk.drabikp.bzscraper.places.domain;

import org.jmolecules.ddd.annotation.ValueObject;
import sk.drabikp.bzscraper.gig.domain.Country;
import sk.drabikp.bzscraper.gig.domain.TextFold;

import java.util.List;
import java.util.Optional;

/**
 * Pure domain service: which of several same-named towns a gig means. A candidate must have
 * the name (accents and case ignored) and, when known, the country; then the district
 * ("okres Přerov"), or else the postal area (the postal code's first two digits — big
 * cities have many codes). Exactly one fit or nothing: guessing put gigs in the wrong town
 * before (Hranice near Cheb instead of Hranice na Moravě).
 */
@ValueObject
public final class TownChoice {

    private TownChoice() {
    }

    public static Optional<Town> pick(String name, Country country, String district, String postalCode,
                                      List<Town> candidates) {
        List<Town> fits = candidates.stream()
                .filter(t -> TextFold.fold(t.name()).equals(TextFold.fold(name)))
                .filter(t -> country == null || t.country() == country)
                .toList();
        if (district != null) {
            fits = fits.stream().filter(t -> TextFold.fold(district).equals(TextFold.fold(t.district()))).toList();
        } else if (postalArea(postalCode) != null && fits.size() > 1) {
            List<Town> sameArea = fits.stream()
                    .filter(t -> postalArea(postalCode).equals(postalArea(t.postalCode()))).toList();
            fits = sameArea;
        }
        return fits.size() == 1 ? Optional.of(fits.getFirst()) : Optional.empty();
    }

    /** The first two digits of a postal code ("753 01" → "75"), or null. */
    static String postalArea(String postalCode) {
        if (postalCode == null) {
            return null;
        }
        String digits = postalCode.replaceAll("\\D", "");
        return digits.length() >= 2 ? digits.substring(0, 2) : null;
    }
}
