package sk.drabikp.bzscraper.sync.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import sk.drabikp.bzscraper.sync.domain.SyncLogEntry;

import java.time.Instant;

/** JPA persistence model for one line of a sync task's history. */
@Entity
@Table(name = "sync_log")
class SyncLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long taskId;
    private Instant loggedAt;
    private String message;

    protected SyncLogEntity() {
        // for JPA
    }

    SyncLogEntity(long taskId, Instant at, String message) {
        this.taskId = taskId;
        this.loggedAt = at;
        this.message = SyncTaskEntity.clip(message, SyncTaskEntity.TEXT_MAX);
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
