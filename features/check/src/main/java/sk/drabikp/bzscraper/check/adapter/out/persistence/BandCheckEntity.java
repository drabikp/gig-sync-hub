package sk.drabikp.bzscraper.check.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** JPA persistence model for a band's latest platform check (table {@code band_check}); the result as JSON. */
@Entity
@Table(name = "band_check")
class BandCheckEntity {

    @Id
    private UUID bandId;
    private String result;
    private Instant checkedAt;

    public BandCheckEntity() {
        // for JPA
    }

    public UUID getBandId() {
        return bandId;
    }

    public void setBandId(UUID bandId) {
        this.bandId = bandId;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }

    public void setCheckedAt(Instant checkedAt) {
        this.checkedAt = checkedAt;
    }
}
