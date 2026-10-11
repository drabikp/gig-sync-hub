package sk.drabikp.bzscraper.accounts.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** JPA persistence model for a change made in a band (table {@code audit_event}). */
@Entity
@Table(name = "audit_event")
class AuditEventEntity {

    @Id
    // from a sequence, drawn 50 at a time, so these inserts can be BATCHED — an identity column
    // makes the driver fetch each row's id back and costs a round trip per row
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "auditEventSeq")
    @SequenceGenerator(name = "auditEventSeq", sequenceName = "audit_event_seq", allocationSize = 50)
    private Long id;
    private UUID bandId;
    private UUID userId;
    private String action;
    private String target;
    private int status;
    private Instant at;

    public AuditEventEntity() {
        // for JPA and AccountsEntityMapper
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UUID getBandId() {
        return bandId;
    }

    public void setBandId(UUID bandId) {
        this.bandId = bandId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public Instant getAt() {
        return at;
    }

    public void setAt(Instant at) {
        this.at = at;
    }
}
