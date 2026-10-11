package sk.drabikp.bzscraper.calendar.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** JPA persistence model for a calendar event's link to a catalog gig (its {@code GigId}). */
@Entity
@Table(name = "calendar_link")
@IdClass(BandEventKey.class)
class CalendarLinkEntity {

    @Id
    private UUID bandId;
    @Id
    private String eventUid;
    private UUID gigId;
    private Instant linkedAt;

    protected CalendarLinkEntity() {
        // for JPA
    }

    CalendarLinkEntity(UUID bandId, String eventUid, UUID gigId, Instant linkedAt) {
        this.bandId = bandId;
        this.eventUid = eventUid;
        this.gigId = gigId;
        this.linkedAt = linkedAt;
    }

    String getEventUid() {
        return eventUid;
    }

    UUID getGigId() {
        return gigId;
    }
}
