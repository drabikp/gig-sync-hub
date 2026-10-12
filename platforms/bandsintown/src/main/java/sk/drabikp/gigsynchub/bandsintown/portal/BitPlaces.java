package sk.drabikp.gigsynchub.bandsintown.portal;

import sk.drabikp.gigsynchub.gig.domain.Address;
import sk.drabikp.gigsynchub.gig.domain.Coordinates;
import sk.drabikp.gigsynchub.gig.domain.Gig;
import sk.drabikp.gigsynchub.gig.domain.SamePlace;

import java.util.Locale;
import java.util.OptionalDouble;

/**
 * Where Bandsintown put a gig. An upload carries no coordinates — the template has no such columns —
 * so the portal works the place out from the row's text, and with little more than a village's name it
 * has put an event 71 km away (Luková, 2026-10-09). This is what measures that: how far the event ended
 * up from the gig's town, and how to say it.
 */
public final class BitPlaces {

    private BitPlaces() {
    }

    /**
     * How far Bandsintown put the event from the gig's town, when that is too far to be the same
     * place — empty when it is near enough, when the portal didn't say where it put it, and when the
     * town has no whereabouts of its own (it was typed in rather than picked from the place search,
     * so there is nothing to measure against and nothing better to offer the portal either).
     */
    public static OptionalDouble kilometresOff(Gig gig, BitEvent event) {
        Address town = gig.location().address();
        if (event.latitude() == null || event.longitude() == null) {
            return OptionalDouble.empty();
        }
        return SamePlace.tooFarFrom(town, new Coordinates(event.latitude(), event.longitude()));
    }

    /**
     * A note for a place that is wrong and that only the user can put right — the gig's town has no
     * whereabouts to send, or sending them didn't take. Null when the place is near enough.
     */
    public static String check(Gig gig, BitEvent event) {
        OptionalDouble km = kilometresOff(gig, event);
        return km.isEmpty() ? null : String.format(Locale.ROOT,
                "Bandsintown placed it %.0f km from %s (%s) — check the place there", km.getAsDouble(),
                gig.location().city(), gig.location().address().district());
    }

    /** A note for a place the upload guessed wrong and that has since been set from the town itself. */
    public static String setFromTheTown(double km, Gig gig) {
        return String.format(Locale.ROOT, "the upload placed it %.0f km from %s, so the place was set from "
                + "the town itself", km, gig.location().city());
    }
}
