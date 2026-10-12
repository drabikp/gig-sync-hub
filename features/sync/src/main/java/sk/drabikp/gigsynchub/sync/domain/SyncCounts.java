package sk.drabikp.gigsynchub.sync.domain;

import org.jmolecules.ddd.annotation.ValueObject;

import java.util.Collection;

/** The open platform work by what it is doing now: waiting its turn, running, failed and tried again later, or failed for good. */
@ValueObject
public record SyncCounts(long queued, long running, long retrying, long failed) {

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
