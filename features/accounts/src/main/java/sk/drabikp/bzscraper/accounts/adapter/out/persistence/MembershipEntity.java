package sk.drabikp.bzscraper.accounts.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** JPA persistence model for who manages which band (table {@code membership}). */
@Entity
@Table(name = "membership")
@IdClass(MembershipEntity.Key.class)
class MembershipEntity {

    static final class Key implements Serializable {

        private UUID userId;
        private UUID bandId;

        Key() {
            // for JPA
        }

        Key(UUID userId, UUID bandId) {
            this.userId = userId;
            this.bandId = bandId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(userId, k.userId) && Objects.equals(bandId, k.bandId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, bandId);
        }
    }

    @Id
    private UUID userId;
    @Id
    private UUID bandId;
    private String role;
    private String displayName;
    private Instant createdAt;

    public MembershipEntity() {
        // for JPA and AccountsEntityMapper
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getBandId() {
        return bandId;
    }

    public void setBandId(UUID bandId) {
        this.bandId = bandId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
