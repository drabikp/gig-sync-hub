package sk.drabikp.bzscraper.sync.domain;

import org.jmolecules.ddd.annotation.ValueObject;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * One gig's unfinished work on the platforms, as it stood when a change to that gig was being
 * queued — which is what decides what to queue. A task reads the gig when it RUNS, not when it is
 * asked for, so work that is still waiting will carry whatever the gig says by then: queueing a
 * second one of it would do the same thing twice. And a change that undoes one the platform hasn't
 * seen yet (a cancel on a waiting reactivate) leaves the platform nothing to be told but the gig as
 * it now is.
 *
 * <p>Read once for the whole request, because what gets queued on one platform doesn't change what
 * is open on another.
 */
@ValueObject
public record OpenWork(List<SyncTask> tasks) {

    public OpenWork {
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
    }

    /** The gig's unfinished tasks, as the outbox has them. */
    public static OpenWork of(List<SyncTask> unfinished) {
        return new OpenWork(unfinished);
    }

    /** Work of this action is open on the platform — waiting its turn or running right now. */
    public boolean has(Platform platform, SyncAction action) {
        return tasks.stream().anyMatch(on(platform, action));
    }

    /** Work of this action is still WAITING on the platform, so the platform hasn't seen it. */
    public boolean pending(Platform platform, SyncAction action) {
        return tasks.stream().anyMatch(on(platform, action).and(t -> t.status() == SyncStatus.PENDING));
    }

    /**
     * An update of the gig is already on its way there: the publish or the update that is waiting
     * will carry the gig's newest details when it runs, so one more would be the same work again.
     */
    public boolean updateAlreadyComing(Platform platform) {
        return pending(platform, SyncAction.PUBLISH) || pending(platform, SyncAction.UPDATE);
    }

    /**
     * This change undoes one the platform hasn't seen yet, so neither of them needs to happen there
     * — what the platform needs instead is the gig as it now is ({@link SyncAction#opposite()}).
     */
    public boolean undoesWaitingWork(Platform platform, SyncAction action) {
        return action.opposite().filter(waiting -> pending(platform, waiting)).isPresent();
    }

    private static Predicate<SyncTask> on(Platform platform, SyncAction action) {
        Objects.requireNonNull(platform, "the platform");
        return task -> task.platform().equals(platform) && task.action() == action;
    }
}
