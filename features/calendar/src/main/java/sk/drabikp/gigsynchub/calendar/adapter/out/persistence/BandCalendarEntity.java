package sk.drabikp.gigsynchub.calendar.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** JPA persistence model for where a band's calendar is read from (table {@code band_calendar}); the address sealed. */
@Entity
@Table(name = "band_calendar")
class BandCalendarEntity {

    @Id
    private UUID bandId;
    private byte[] icalUrl;
    private String keyId;
    private String zone;
    private Instant updatedAt;

    public BandCalendarEntity() {
        // for JPA
    }

    public UUID getBandId() {
        return bandId;
    }

    public void setBandId(UUID bandId) {
        this.bandId = bandId;
    }

    public byte[] getIcalUrl() {
        return icalUrl;
    }

    public void setIcalUrl(byte[] icalUrl) {
        this.icalUrl = icalUrl;
    }

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
