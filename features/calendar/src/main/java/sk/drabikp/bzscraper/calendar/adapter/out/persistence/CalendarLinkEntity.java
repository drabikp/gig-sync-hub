package sk.drabikp.bzscraper.calendar.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** JPA persistence model for a calendar event's link to a catalog gig (serialized GigId, as in {@link GigEntity}). */
@Entity
@Table(name = "calendar_link")
@IdClass(BandEventKey.class)
class CalendarLinkEntity {

    @Id
    private UUID bandId;
    @Id
    private String eventUid;
    private String gigId;
    private Instant linkedAt;

    protected CalendarLinkEntity() {
        // for JPA
    }

    CalendarLinkEntity(UUID bandId, String eventUid, String gigId, Instant linkedAt) {
        this.bandId = bandId;
        this.eventUid = eventUid;
        this.gigId = gigId;
        this.linkedAt = linkedAt;
    }

    String getEventUid() {
        return eventUid;
    }

    String getGigId() {
        return gigId;
    }

    void moveTo(String newGigId) {
        gigId = newGigId;
    }
}
