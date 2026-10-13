package sk.drabikp.gigsynchub.sync.application.engine;

import org.jmolecules.architecture.hexagonal.Application;
import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.sync.domain.Lane;
import sk.drabikp.gigsynchub.sync.domain.SyncTask;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Whose work runs next: the bands take turns (round-robin, in a fixed order), so a band with a hundred
 * tasks doesn't keep the others waiting; within a band its oldest due task goes first. A lane (a band's
 * work on a platform) is run by one worker at a time — its runs are claimed until released.
 *
 * <p><b>It decides whose turn it is before reading anyone's work.</b> It used to be handed every due
 * task on the server and pick one out of the list, which the worker then did again for the next
 * workload, and again — so draining N tasks read N× every band's due rows, and the cost of DECIDING
 * grew with the square of the backlog while the work itself stayed linear. Now it is given the bands
 * that have work due, which is one row per band, and reads the due tasks of the ONE band whose turn
 * it is.
 */
@Application
public final class FairTurns {

    private final Set<Lane> running = new HashSet<>();
    private BandId last;

    /**
     * Claims a task of the next band in turn: the first band after the one served last whose due
     * work has a task that {@code may} run and whose lane isn't already running. {@code dueOf} is
     * asked for one band's work at a time, and only until a band yields a claimable task.
     *
     * @param withWorkDue the bands that have work due at all — small, one entry per band
     * @param dueOf       that band's due tasks, oldest first
     */
    public synchronized Optional<SyncTask> claim(List<BandId> withWorkDue, Function<BandId, List<SyncTask>> dueOf,
                                                 Predicate<SyncTask> may) {
        for (BandId band : inTurn(withWorkDue)) {
            Optional<SyncTask> next = dueOf.apply(band).stream()
                    .filter(task -> !running.contains(Lane.of(task)))
                    .filter(may)
                    .findFirst();
            if (next.isPresent()) {
                running.add(Lane.of(next.get()));
                last = band;
                return next;
            }
        }
        return Optional.empty();
    }

    /**
     * The bands in the order their turn comes: those after the one served last, then round to the
     * beginning. Every band is tried, because the one whose turn it is may have nothing claimable —
     * all of its work on a lane a worker already holds, say — and the turn then passes on.
     */
    private List<BandId> inTurn(List<BandId> bands) {
        Comparator<BandId> order = Comparator.comparing(BandId::value);
        List<BandId> sorted = bands.stream().sorted(order).toList();
        if (last == null) {
            return sorted;
        }
        List<BandId> after = sorted.stream().filter(b -> order.compare(b, last) > 0).toList();
        List<BandId> upTo = sorted.stream().filter(b -> order.compare(b, last) <= 0).toList();
        return java.util.stream.Stream.concat(after.stream(), upTo.stream()).toList();
    }

    public synchronized void release(Lane lane) {
        running.remove(lane);
    }

    /** The lanes being run right now. */
    public synchronized Set<Lane> running() {
        return Set.copyOf(running);
    }
}
