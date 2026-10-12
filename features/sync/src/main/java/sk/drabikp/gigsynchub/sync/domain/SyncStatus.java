package sk.drabikp.gigsynchub.sync.domain;

import org.jmolecules.ddd.annotation.ValueObject;

/** Where a sync task is. */
@ValueObject
public enum SyncStatus {
    /** Waiting to run (first time, or for its next retry). */
    PENDING,
    RUNNING,
    DONE,
    /** Gave up — needs the user: Retry or Discard. */
    FAILED,
    /** Dropped: superseded by a later change, or discarded by the user. */
    DISCARDED;

    /** Not settled yet: the platform may still change. */
    public boolean open() {
        return this == PENDING || this == RUNNING;
    }

    /**
     * The changes a task's status may make — done and discarded are final; a failed task waits
     * for the user (retry → pending, or discard); a running one only ends (done, failed, or
     * pending for a retry or the next step). Anything else is a late or racing write to ignore.
     */
    public boolean canBecome(SyncStatus next) {
        return switch (this) {
            case PENDING -> next == RUNNING || next == PENDING || next == DONE || next == FAILED || next == DISCARDED;
            case RUNNING -> next == DONE || next == FAILED || next == PENDING;
            case FAILED -> next == PENDING || next == DISCARDED;
            case DISCARDED -> next == PENDING;             // the user's Retry, nothing else
            case DONE -> false;
        };
    }
}
