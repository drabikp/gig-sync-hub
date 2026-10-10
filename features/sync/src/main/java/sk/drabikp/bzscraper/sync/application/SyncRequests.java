package sk.drabikp.bzscraper.sync.application;

import sk.drabikp.bzscraper.gig.application.port.out.PublishedGigStore;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.gig.domain.platform.Platforms;
import sk.drabikp.bzscraper.sync.application.engine.SyncAdmission;
import sk.drabikp.bzscraper.sync.application.port.in.QueueSyncWork;
import sk.drabikp.bzscraper.sync.application.port.out.SyncNotifier;
import sk.drabikp.bzscraper.sync.application.port.out.SyncOutbox;
import sk.drabikp.bzscraper.sync.application.port.out.SyncTrigger;
import sk.drabikp.bzscraper.sync.domain.QueueResult;
import sk.drabikp.bzscraper.sync.domain.SyncAction;
import sk.drabikp.bzscraper.sync.domain.SyncStatus;
import sk.drabikp.bzscraper.sync.domain.SyncTask;

import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * What a catalog change puts in the sync outbox ({@link QueueSyncWork}). A task reads the gig's state when it runs, so:
 * <ul>
 *   <li>an update is not queued again while one (or the publish) is still pending —
 *       that one will carry the newest details;</li>
 *   <li>deleting a gig discards its pending tasks and queues one delete per platform the
 *       gig is actually on;</li>
 *   <li>a cancel and a reactivate that both haven't run yet cancel out;</li>
 *   <li>a new task replaces the FAILED tasks whose work it does (a new publish the failed
 *       publish, a delete the failed updates/cancels), so they stop waiting for the user;</li>
 *   <li>work none of the platform's steps takes (e.g. edits of past events where no step can) is not
 *       queued — it is listed as left out, with what to do by hand; a delete then forgets
 *       the platform copy.</li>
 * </ul>
 */
public class SyncRequests implements QueueSyncWork {

    /**
     * What a delete replaces. A failed publish or reactivate stays: it may have created an
     * event on the platform anyway, and only the user can check that.
     */
    private static final Set<SyncAction> REPEATABLE = EnumSet.of(SyncAction.UPDATE, SyncAction.CANCEL, SyncAction.DELETE);

    private final SyncOutbox outbox;
    private final PublishedGigStore publishedGigStore;
    private final SyncTrigger trigger;
    private final SyncNotifier notifier;
    private final Clock clock;
    private final SyncAdmission admission;
    private final Platforms platforms;

    public SyncRequests(SyncOutbox outbox, PublishedGigStore publishedGigStore, SyncTrigger trigger,
                        SyncNotifier notifier, Clock clock, SyncAdmission admission, Platforms platforms) {
        this.outbox = outbox;
        this.publishedGigStore = publishedGigStore;
        this.trigger = trigger;
        this.notifier = notifier;
        this.clock = clock;
        this.admission = admission;
        this.platforms = platforms;
    }

    /** Tells the worker and the pages; only if something was queued. */
    @Override
    public void signal(BandId band, QueueResult result) {
        if (!result.queued().isEmpty()) {
            trigger.wake();
            notifier.changed(band);
        }
    }

    @Override
    public QueueResult update(BandId band, Gig gig) {
        List<SyncTask> queued = new ArrayList<>();
        List<String> leftOut = new ArrayList<>();
        OpenWork open = openWork(band, gig.id());
        for (Platform platform : platforms.all()) {
            boolean pendingPublish = open.pending(platform, SyncAction.PUBLISH);
            if (!publishedGigStore.isPublished(band, platform, gig.id()) && !pendingPublish) {
                continue;
            }
            if (!allowed(gig, platform, SyncAction.UPDATE, leftOut)) {
                continue;
            }
            if (!pendingPublish && !open.pending(platform, SyncAction.UPDATE)) {
                queued.add(enqueue(band, gig, platform, SyncAction.UPDATE));
            }
        }
        return new QueueResult(queued, leftOut);
    }

    @Override
    public QueueResult cancel(BandId band, Gig gig) {
        return onPublishedPlatforms(band, gig, SyncAction.CANCEL, SyncAction.REACTIVATE);
    }

    @Override
    public QueueResult reactivate(BandId band, Gig gig) {
        return onPublishedPlatforms(band, gig, SyncAction.REACTIVATE, SyncAction.CANCEL);
    }

    /**
     * {@code gig} is the deleted catalog gig (null if it wasn't there). Where the platform
     * can't remove it (a past event), its record is forgotten and the user told to delete
     * it there by hand.
     */
    @Override
    public QueueResult delete(BandId band, GigId id, Gig gig) {
        String label = gig != null ? SyncTask.labelOf(gig) : SyncTask.labelOf(id);
        List<SyncTask> queued = new ArrayList<>();
        List<String> leftOut = new ArrayList<>();
        for (Platform platform : platforms.all()) {
            outbox.supersede(band, id, platform, "the gig was deleted", clock.instant());
            outbox.replaceFailed(band, id, platform, REPEATABLE, "the gig was deleted", clock.instant());
            if (!publishedGigStore.isPublished(band, platform, id)) {
                continue;
            }
            if (gig != null && !allowed(gig, platform, SyncAction.DELETE, leftOut)) {
                publishedGigStore.remove(band, platform, id);
                continue;
            }
            SyncTask task = outbox.enqueue(band, id, label, platform, SyncAction.DELETE, clock.instant());
            queued.add(task);
        }
        return new QueueResult(queued, leftOut);
    }

    QueueResult publish(BandId band, Platform platform, Gig gig) {
        Function<String, QueueResult> skipped = why ->
                new QueueResult(List.of(), List.of(gig.title() + " on " + platforms.name(platform) + ": " + why));
        if (gig.cancelled()) {
            return skipped.apply("cancelled");
        }
        if (publishedGigStore.isPublished(band, platform, gig.id())) {
            return skipped.apply("already published");
        }
        if (openWork(band, gig.id()).has(platform, SyncAction.PUBLISH)) {
            return skipped.apply("already being published");
        }
        Optional<String> noStep = admission.leftOut(platform, SyncAction.PUBLISH, gig);
        if (noStep.isPresent()) {
            return skipped.apply(noStep.get());
        }
        return new QueueResult(List.of(enqueue(band, gig, platform, SyncAction.PUBLISH)), List.of());
    }

    @Override
    public void move(BandId band, GigId from, Gig to) {
        if (!from.equals(to.id())) {
            outbox.move(band, from, to.id(), SyncTask.labelOf(to));
        }
    }

    /**
     * Queues {@code action} on every platform the gig is on — unless the {@code opposite}
     * change is still pending there: the platform never saw it, so both are dropped and an
     * update brings the copy to the gig's current details instead.
     */
    private QueueResult onPublishedPlatforms(BandId band, Gig gig, SyncAction action, SyncAction opposite) {
        List<SyncTask> queued = new ArrayList<>();
        List<String> leftOut = new ArrayList<>();
        OpenWork open = openWork(band, gig.id());
        for (Platform platform : platforms.all()) {
            if (!publishedGigStore.isPublished(band, platform, gig.id())) {
                continue;
            }
            if (open.pending(platform, opposite)) {
                outbox.supersede(band, gig.id(), platform, "undone by " + action.verb() + " before it ran",
                        clock.instant());
                if (allowed(gig, platform, SyncAction.UPDATE, leftOut)) {
                    queued.add(enqueue(band, gig, platform, SyncAction.UPDATE));
                }
            } else if (allowed(gig, platform, action, leftOut)) {
                queued.add(enqueue(band, gig, platform, action));
            }
        }
        return new QueueResult(queued, leftOut);
    }

    /** Whether one of the platform's steps takes the action on this gig; if not, says why in {@code leftOut}. */
    private boolean allowed(Gig gig, Platform platform, SyncAction action, List<String> leftOut) {
        Optional<String> noStep = admission.leftOut(platform, action, gig);
        noStep.ifPresent(why -> leftOut.add(gig.title() + ": " + why));
        return noStep.isEmpty();
    }

    /**
     * The gig's open work as it was before this request — read once: what the request queues on one
     * platform doesn't change what is open on another.
     */
    private OpenWork openWork(BandId band, GigId id) {
        return new OpenWork(outbox.openFor(band, id));
    }

    private record OpenWork(List<SyncTask> tasks) {

        /** Work of this action is pending or running there. */
        boolean has(Platform platform, SyncAction action) {
            return tasks.stream().anyMatch(t -> t.platform().equals(platform) && t.action() == action);
        }

        /** Work of this action is still waiting there (the platform hasn't seen it). */
        boolean pending(Platform platform, SyncAction action) {
            return tasks.stream().anyMatch(t -> t.platform().equals(platform) && t.action() == action
                    && t.status() == SyncStatus.PENDING);
        }
    }

    private SyncTask enqueue(BandId band, Gig gig, Platform platform, SyncAction action) {
        SyncTask task = outbox.enqueue(band, gig.id(), SyncTask.labelOf(gig), platform, action, clock.instant());
        outbox.replaceFailed(band, gig.id(), platform, replacedBy(action),
                "a new " + action.verb() + " was queued (#" + task.id() + ")", clock.instant());
        return task;
    }

    /** The failed tasks whose work a new {@code action} does. */
    private static Set<SyncAction> replacedBy(SyncAction action) {
        return switch (action) {
            case PUBLISH -> EnumSet.of(SyncAction.PUBLISH);
            case UPDATE -> EnumSet.of(SyncAction.UPDATE);
            case CANCEL -> EnumSet.of(SyncAction.CANCEL, SyncAction.UPDATE);
            case REACTIVATE -> EnumSet.of(SyncAction.REACTIVATE, SyncAction.UPDATE);
            case DELETE -> REPEATABLE;
        };
    }

}
