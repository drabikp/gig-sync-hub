package sk.drabikp.bzscraper.sync.domain;

import org.jmolecules.ddd.annotation.ValueObject;

import java.time.Instant;

/** One line of a sync task's history: queued, attempt started, done, failed, retried, discarded. */
@ValueObject
public record SyncLogEntry(long taskId, Instant at, String message) {
}
