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
import java.util.function.Predicate;

/**
 * Whose work runs next: the bands take turns (round-robin, in a fixed order), so a band with a hundred
 * tasks doesn't keep the others waiting; within a band its oldest due task goes first. A lane (a band's
 * work on a platform) is run by one worker at a time — its runs are claimed until released.
 */
@Application
public final class FairTurns {

    private final Set<Lane> running = new HashSet<>();
    private BandId last;

    /**
     * Claims the next task among {@code due} (oldest first) that {@code may} run and whose lane isn't running:
     * the first band after the one served last that has one.
     */
    public synchronized Optional<SyncTask> claim(List<SyncTask> due, Predicate<SyncTask> may) {
        List<SyncTask> eligible = due.stream().filter(t -> !running.contains(Lane.of(t))).filter(may).toList();
        if (eligible.isEmpty()) {
            return Optional.empty();
        }
        Comparator<BandId> order = Comparator.comparing(BandId::value);
        BandId band = eligible.stream().map(SyncTask::band).filter(b -> last == null || order.compare(b, last) > 0)
                .min(order)
                .orElseGet(() -> eligible.stream().map(SyncTask::band).min(order).orElseThrow());
        SyncTask next = eligible.stream().filter(t -> t.band().equals(band)).findFirst().orElseThrow();
        running.add(Lane.of(next));
        last = band;
        return Optional.of(next);
    }

    public synchronized void release(Lane lane) {
        running.remove(lane);
    }

    /** The lanes being run right now. */
    public synchronized Set<Lane> running() {
        return Set.copyOf(running);
    }
}
