package sk.drabikp.bzscraper.gig.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * JPA persistence model for one published-gig record: a catalog gig (by serialized
 * {@link sk.drabikp.bzscraper.gig.domain.GigId}, same format as {@link GigEntity}'s id)
 * on one platform, with the platform's id for it ({@code externalRef}, null if unknown).
 */
@Entity
@Table(name = "published_gig")
class PublishedGigEntity {

    @EmbeddedId
    private Key key;
    private String externalRef;

    protected PublishedGigEntity() {
        // for JPA
    }

    public PublishedGigEntity(Key key, String externalRef) {
        this.key = key;
        this.externalRef = externalRef;
    }

    public Key getKey() {
        return key;
    }

    public String getExternalRef() {
        return externalRef;
    }

    @Embeddable
    public record Key(
            @Column(name = "band_id") UUID bandId,
            String platform,
            @Column(name = "gig_id") UUID gigId) {
    }
}
