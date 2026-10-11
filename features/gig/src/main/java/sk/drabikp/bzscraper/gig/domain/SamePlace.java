package sk.drabikp.bzscraper.gig.domain;

import java.util.OptionalDouble;

/**
 * Whether a platform put a gig where its town actually is. Neither side can be compared exactly: a
 * town's own point is its centre, and a platform works the place out from the text it was given —
 * which is how one of them once put a gig in a same-named town a country away. So the question is
 * never "the same point" but "near enough to be the same town", and what separates the two is
 * {@value #KILOMETRES} km: further than a town is wide, closer than the next town of that name.
 *
 * <p>One rule, because two places ask it about the same thing — the nightly check, which calls the
 * difference a drift for the user to look at, and a publish on a platform that works places out
 * from text, which adds a note to the task. Each had its own copy of the number before, under its
 * own name.
 */
public final class SamePlace {

    /** How far from its town a gig may be placed and still be in it. */
    public static final double KILOMETRES = 25;

    private SamePlace() {
    }

    /**
     * How far {@code placed} is from the town, when that is further than the same place — empty when
     * it is near enough, and when there is nothing to compare: a town only has a point of its own
     * once it was picked from the place search, and a platform need not say where it put anything.
     */
    public static OptionalDouble tooFarFrom(Address town, Coordinates placed) {
        if (town == null || placed == null || !town.resolved()) {
            return OptionalDouble.empty();
        }
        OptionalDouble km = town.kilometresTo(placed);
        return km.isPresent() && km.getAsDouble() > KILOMETRES ? km : OptionalDouble.empty();
    }
}
