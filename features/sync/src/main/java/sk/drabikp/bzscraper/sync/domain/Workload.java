package sk.drabikp.bzscraper.sync.domain;

import org.jmolecules.ddd.annotation.ValueObject;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;

import java.util.Objects;

/**
 * A band's work of one action on one platform: the runs that share a workflow and are therefore
 * done together — one upload carries twenty-five gigs, and a platform's form takes them one after
 * another in the session it already has open.
 *
 * <p>This is what the engine is given: not a task, because a task on its own cannot say which
 * batch it belongs in. One worker does one workload at a time, because a {@link Lane} — the band's
 * account on the platform — is never used by two at once.
 */
@ValueObject
public record Workload(Lane lane, SyncAction action) {

    public Workload {
        Objects.requireNonNull(lane, "the lane the work is done in");
        Objects.requireNonNull(action, "what the work does to the gigs");
    }

    /** The workload a due task belongs to: its band's work of its action on its platform. */
    public static Workload of(SyncTask task) {
        return new Workload(Lane.of(task), task.action());
    }

    public BandId band() {
        return lane.band();
    }

    public Platform platform() {
        return lane.platform();
    }
}
