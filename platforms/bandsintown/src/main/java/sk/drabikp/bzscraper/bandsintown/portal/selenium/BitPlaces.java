package sk.drabikp.bzscraper.bandsintown.portal.selenium;

import sk.drabikp.bzscraper.gig.domain.Address;
import sk.drabikp.bzscraper.gig.domain.CityName;
import sk.drabikp.bzscraper.gig.domain.Coordinates;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.Location;
import sk.drabikp.bzscraper.gig.domain.TextFold;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Where Bandsintown puts a gig. {@link #choose} picks the gig's place among the venue
 * search's suggestions (Google places): only a place in the gig's town, and among those the
 * one named like the venue, else the first. {@link #check} compares where Bandsintown put an
 * event with the gig's town.
 */
final class BitPlaces {

    /** How far from the gig's town Bandsintown may place it before the user is told to check. */
    private static final double TOLERANCE_KM = 25;

    private BitPlaces() {
    }

    /**
     * What to type into the venue search: "venue town"; without a venue "town district country"
     * — the district ("okres Ústí nad Orlicí" → "Ústí nad Orlicí") puts the right one of several
     * same-named villages first.
     */
    static String query(Gig gig) {
        Location location = gig.location();
        if (location.venue() != null) {
            return location.venue() + " " + location.city();
        }
        String district = location.address() == null || location.address().district() == null ? ""
                : location.address().district().replaceFirst("^(?i)okres\\s+", "") + " ";
        return location.city() + " " + district + location.countryName();
    }

    static BitPlace choose(List<BitPlace> places, String venue, String city) {
        List<BitPlace> inTown = places.stream().filter(p -> inTown(p, city)).toList();
        if (venue != null) {
            String wanted = TextFold.fold(venue);
            for (BitPlace place : inTown) {
                String name = TextFold.fold(place.name());
                if (name.contains(wanted) || wanted.contains(name)) {
                    return place;
                }
            }
        }
        return inTown.isEmpty() ? null : inTown.getFirst();
    }

    /**
     * Bandsintown works out the place from the uploaded text and has picked the wrong one of
     * same-named towns before: compares its coordinates with the town's (when the town was
     * picked from the place search) — a note when they are far apart, else null.
     */
    public static String check(Gig gig, BitEvent event) {
        Address address = gig.location().address();
        if (address == null || !address.resolved() || event.latitude() == null || event.longitude() == null) {
            return null;
        }
        double km = address.kilometresTo(new Coordinates(event.latitude(), event.longitude())).orElse(0);
        return km <= TOLERANCE_KM ? null : String.format(Locale.ROOT,
                "Bandsintown placed it %.0f km from %s (%s) — check the place there", km,
                gig.location().city(), address.district());
    }

    /** One of the description's comma-separated parts is the town (accents, district numbers ignored). */
    private static boolean inTown(BitPlace place, String city) {
        String key = CityName.key(city);
        return Arrays.stream(place.description().split(","))
                .map(String::strip)
                .anyMatch(part -> CityName.key(part).equals(key));
    }
}
