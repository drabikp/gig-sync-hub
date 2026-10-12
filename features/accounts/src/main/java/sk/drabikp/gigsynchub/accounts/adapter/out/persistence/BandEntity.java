package sk.drabikp.gigsynchub.accounts.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** JPA persistence model for a band (table {@code band}). */
@Entity
@Table(name = "band")
class BandEntity {

    @Id
    private UUID id;
    private String slug;
    private String name;
    private boolean publicList;
    private Instant createdAt;

    public BandEntity() {
        // for JPA and AccountsEntityMapper
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isPublicList() {
        return publicList;
    }

    public void setPublicList(boolean publicList) {
        this.publicList = publicList;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
