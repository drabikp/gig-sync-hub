package sk.drabikp.gigsynchub.sync.domain;

import org.jmolecules.ddd.annotation.ValueObject;

import java.util.Collection;

/** The open platform work by what it is doing now: waiting its turn, running, failed and tried again later, or failed for good. */
@ValueObject
public record SyncCounts(long queued, long running, long retrying, long failed) {

    /** A band with nothing open — what a store that counted no rows for it means. */
    public static final SyncCounts NONE = new SyncCounts(0, 0, 0, 0);

    /**
     * These counts and {@code howMany} more tasks of that status — how a store that counts rows
     * itself builds the same answer the loop below builds from the rows themselves.
     */
    public SyncCounts and(SyncStatus status, boolean retrying, long howMany) {
        return switch (status) {
            case PENDING -> retrying
                    ? new SyncCounts(queued, running, this.retrying + howMany, failed)
                    : new SyncCounts(queued + howMany, running, this.retrying, failed);
            case RUNNING -> new SyncCounts(queued, running + howMany, this.retrying, failed);
            case FAILED -> new SyncCounts(queued, running, this.retrying, failed + howMany);
            default -> this;
        };
    }

    /** Two partial counts of the same band, from two of the store's rows. */
    public SyncCounts plus(SyncCounts other) {
        return new SyncCounts(queued + other.queued, running + other.running,
                retrying + other.retrying, failed + other.failed);
    }

    public static SyncCounts of(Collection<SyncTask> tasks) {
        long queued = 0;
        long running = 0;
        long retrying = 0;
        long failed = 0;
        for (SyncTask t : tasks) {
            switch (t.status()) {
                case PENDING -> {
                    if (t.retrying()) {
                        retrying++;
                    } else {
                        queued++;
                    }
                }
                case RUNNING -> running++;
                case FAILED -> failed++;
                default -> {
                }
            }
        }
        return new SyncCounts(queued, running, retrying, failed);
    }
}
