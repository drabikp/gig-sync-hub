package sk.drabikp.bzscraper.accounts.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** JPA persistence model for a user (table {@code app_user}). */
@Entity
@Table(name = "app_user")
class AppUserEntity {

    @Id
    private UUID id;
    private String rauthySub;
    private String email;
    private String displayName;
    private Instant createdAt;

    public AppUserEntity() {
        // for JPA and AccountsEntityMapper
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getRauthySub() {
        return rauthySub;
    }

    public void setRauthySub(String rauthySub) {
        this.rauthySub = rauthySub;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
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
