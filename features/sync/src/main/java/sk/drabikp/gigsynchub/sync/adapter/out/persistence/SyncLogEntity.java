package sk.drabikp.gigsynchub.sync.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import sk.drabikp.gigsynchub.sync.domain.SyncLogEntry;

import java.time.Instant;
import java.util.UUID;

/** JPA persistence model for one line of a sync task's history. */
@Entity
@Table(name = "sync_log")
class SyncLogEntity {

    @Id
    // from a sequence, drawn 50 at a time, so these inserts can be BATCHED — an identity column
    // makes the driver fetch each row's id back and costs a round trip per row
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "syncLogSeq")
    @SequenceGenerator(name = "syncLogSeq", sequenceName = "sync_log_seq", allocationSize = 50)
    private Long id;
    /**
     * Whose line this is. A log line belongs to whichever band its TASK belongs to, which is true
     * and was the whole trouble: with no band of its own the table fell out of every check that
     * works out which tables keep a band's rows (V14).
     */
    private UUID bandId;
    private Long taskId;
    private Instant loggedAt;
    private String message;

    protected SyncLogEntity() {
        // for JPA
    }

    SyncLogEntity(UUID bandId, long taskId, Instant at, String message) {
        this.bandId = bandId;
        this.taskId = taskId;
        this.loggedAt = at;
        this.message = SyncTaskEntity.clip(message, SyncTaskEntity.TEXT_MAX);
    }

    public UUID getBandId() {
        return bandId;
    }

    public Long getTaskId() {
        return taskId;
    }

    public Instant getLoggedAt() {
        return loggedAt;
    }

    public String getMessage() {
        return message;
    }
}
