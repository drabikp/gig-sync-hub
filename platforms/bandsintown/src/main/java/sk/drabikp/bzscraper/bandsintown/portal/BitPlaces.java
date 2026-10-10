package sk.drabikp.bzscraper.bandsintown.portal;

import sk.drabikp.bzscraper.gig.domain.Address;
import sk.drabikp.bzscraper.gig.domain.Coordinates;
import sk.drabikp.bzscraper.gig.domain.Gig;

import java.util.Locale;

/**
 * Where Bandsintown put a gig. It works the place out from the text an upload gives it, and has picked the
 * wrong one of two same-named towns before, so {@link #check} compares where the event ended up with where
 * the gig's town is — a note for the user when they are far apart.
 */
public final class BitPlaces {

    /** How far from the gig's town Bandsintown may place it before the user is told to check. */
    private static final double TOLERANCE_KM = 25;

    private BitPlaces() {
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

}
