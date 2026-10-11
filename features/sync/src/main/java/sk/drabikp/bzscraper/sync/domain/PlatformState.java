package sk.drabikp.bzscraper.sync.domain;

import org.jmolecules.ddd.annotation.ValueObject;

/**
 * Where a gig stands on one platform, as the pages show it: the latest unfinished sync task
 * there decides; without one, whether the gig is published there.
 */
@ValueObject
public enum PlatformState {
    /** Published, nothing to do. */
    LIVE,
    /** Not there. */
    NONE,
    /** Waiting for its first run. */
    QUEUED,
    RUNNING,
    /** Failed, tried again later. */
    RETRYING,
    /** Waits for the user. */
    FAILED;

    /** The state from the latest unfinished task there ({@code null}: none) and whether the gig is published. */
    public static PlatformState of(boolean published, SyncTask latestUnfinished) {
        if (latestUnfinished == null) {
            return published ? LIVE : NONE;
        }
        return switch (latestUnfinished.status()) {
            case FAILED -> FAILED;
            case RUNNING -> RUNNING;
            default -> latestUnfinished.retrying() ? RETRYING : QUEUED;
        };
    }
}
