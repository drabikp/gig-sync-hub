package sk.drabikp.bzscraper.sync.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.sync.domain.StepType;
import sk.drabikp.bzscraper.sync.domain.SyncAction;
import sk.drabikp.bzscraper.sync.domain.SyncStatus;

import java.time.Instant;
import java.util.UUID;

/** JPA persistence model for one sync-outbox task; enums stored by name, the gig id serialized. */
@Entity
@Table(name = "sync_task")
class SyncTaskEntity {

    static final int TEXT_MAX = 2000;

    @Id
    // from a sequence, drawn 50 at a time, so these inserts can be BATCHED — an identity column
    // makes the driver fetch each row's id back and costs a round trip per row
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "syncTaskSeq")
    @SequenceGenerator(name = "syncTaskSeq", sequenceName = "sync_task_seq", allocationSize = 50)
    private Long id;
    private UUID bandId;
    private String gigId;
    private String gigLabel;
    private String platform;
    private String action;
    private String status;
    private int attempts;
    private Instant createdAt;
    private Instant nextAttemptAt;
    private Instant updatedAt;
    private String message;
    private String step;
    /** Optimistic lock: two writers of one task can't both win (the later one fails). */
    @Version
    private Long version;

    protected SyncTaskEntity() {
        // for JPA
    }

    SyncTaskEntity(UUID bandId, String gigId, String gigLabel, Platform platform, SyncAction action, Instant now) {
        this.bandId = bandId;
        this.gigId = gigId;
        this.gigLabel = clip(gigLabel, 500);
        this.platform = platform.id();
        this.action = action.name();
        this.status = SyncStatus.PENDING.name();
        this.attempts = 0;
        this.createdAt = now;
        this.nextAttemptAt = now;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public UUID getBandId() {
        return bandId;
    }

    public String getGigId() {
        return gigId;
    }

    public String getGigLabel() {
        return gigLabel;
    }

    public String getPlatform() {
        return platform;
    }

    public String getAction() {
        return action;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getMessage() {
        return message;
    }

    public String getStep() {
        return step;
    }

    SyncStatus status() {
        return SyncStatus.valueOf(status);
    }

    public int getAttempts() {
        return attempts;
    }

    void moveTo(String newGigId, String newLabel) {
        this.gigId = newGigId;
        this.gigLabel = clip(newLabel, 500);
    }

    /** Applies the change if the status may make it ({@link SyncStatus#canBecome}); false = ignored. */
    boolean change(SyncStatus newStatus, Instant nextAttempt, String newMessage, Instant now) {
        if (!status().canBecome(newStatus)) {
            return false;
        }
        this.status = newStatus.name();
        this.nextAttemptAt = nextAttempt;
        this.message = clip(newMessage, TEXT_MAX);
        this.updatedAt = now;
        return true;
    }

    /** On to another step of the workflow: due now, attempts counted afresh for it. False = ignored. */
    boolean advance(StepType next, Instant now) {
        if (!status().canBecome(SyncStatus.PENDING)) {
            return false;
        }
        this.step = next.name();
        this.status = SyncStatus.PENDING.name();
        this.nextAttemptAt = now;
        this.attempts = 0;
        this.updatedAt = now;
        return true;
    }

    void startAttempt(Instant now) {
        this.status = SyncStatus.RUNNING.name();
        this.attempts++;
        this.nextAttemptAt = null;
        this.updatedAt = now;
    }

    void resetAttempts() {
        this.attempts = 0;
    }

    /** The attempt just started didn't happen after all. */
    void undoAttempt() {
        this.attempts = Math.max(0, attempts - 1);
    }

    static String clip(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
