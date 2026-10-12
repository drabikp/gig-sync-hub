package sk.drabikp.gigsynchub.importing.application.port.in;

import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.gig.domain.platform.Platform;
import sk.drabikp.gigsynchub.importing.domain.ImportPlan;
import sk.drabikp.gigsynchub.importing.domain.ImportResult;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * An import as the user goes through it: reading the platforms takes a while (a browser for
 * some), so {@link #read} starts it in the background and the live updates tell when it ended;
 * the plan is kept until applied or read again; {@link #apply} names its proposals by index.
 * One import at a time.
 */
public interface ImportSessionUseCase {

    /** What can be read, whether a read runs (or why the last one failed), and the plan. */
    State state(BandId band);

    /** Starts reading the platforms (nothing when a read already runs); none picked is refused. */
    void read(BandId band, Collection<Platform> platforms);

    /** Imports what the user decided about the plan's proposals; nothing chosen is refused. */
    ImportResult apply(BandId band, List<Choice> choices);

    /** @param plan null until a read ended, and again after applying */
    record State(Set<Platform> platforms, boolean reading, String readError, ImportPlan plan) {
    }

    /** About the plan's proposal {@code index}: import it, as the same gig, with its version {@code version}. */
    record Choice(int index, boolean include, boolean sameGig, int version) {
    }
}
