package sk.drabikp.gigsynchub.calendar.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/** JPA persistence model for one event of the saved calendar copy (table {@code calendar_event}). */
@Entity
@Table(name = "calendar_event")
@IdClass(BandEventKey.class)
class CalendarEventEntity {

    @Id
    private UUID bandId;
    @Id
    private String eventUid;
    private String title;
    private String location;
    @Column(length = 1_000_000)
    private String notes;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private boolean allDay;
    private boolean repeating;
    private boolean shownAsFree;
    private String calendarStatus;
    private Instant lastModified;
    private String suggested;
    private Instant firstSeen;
    private Instant lastSeen;
    private Instant removedAt;
    private String changeType;
    private String changeFields;
    private String suggestedBefore;
    private Instant changedAt;

    public CalendarEventEntity() {
        // for JPA and CalendarEntityMapper
    }

    CalendarEventEntity(UUID bandId, String eventUid) {
        this.bandId = bandId;
        this.eventUid = eventUid;
    }

    /** A read saw the event at {@code seen}; without one (a change marked seen) the first sighting stays. */
    void seen(Instant seen, Instant firstSeen) {
        if (seen != null || lastSeen == null) {
            lastSeen = seen != null ? seen : firstSeen;
        }
    }

    public UUID getBandId() {
        return bandId;
    }

    public void setBandId(UUID bandId) {
        this.bandId = bandId;
    }

    public String getEventUid() {
        return eventUid;
    }

    public void setEventUid(String eventUid) {
        this.eventUid = eventUid;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDateTime getStartsAt() {
        return startsAt;
    }

    public void setStartsAt(LocalDateTime startsAt) {
        this.startsAt = startsAt;
    }

    public LocalDateTime getEndsAt() {
        return endsAt;
    }

    public void setEndsAt(LocalDateTime endsAt) {
        this.endsAt = endsAt;
    }

    public boolean isAllDay() {
        return allDay;
    }

    public void setAllDay(boolean allDay) {
        this.allDay = allDay;
    }

    public boolean isRepeating() {
        return repeating;
    }

    public void setRepeating(boolean repeating) {
        this.repeating = repeating;
    }

    public boolean isShownAsFree() {
        return shownAsFree;
    }

    public void setShownAsFree(boolean shownAsFree) {
        this.shownAsFree = shownAsFree;
    }

    public String getCalendarStatus() {
        return calendarStatus;
    }

    public void setCalendarStatus(String calendarStatus) {
        this.calendarStatus = calendarStatus;
    }

    public Instant getLastModified() {
        return lastModified;
    }

    public void setLastModified(Instant lastModified) {
        this.lastModified = lastModified;
    }

    public String getSuggested() {
        return suggested;
    }

    public void setSuggested(String suggested) {
        this.suggested = suggested;
    }

    public Instant getFirstSeen() {
        return firstSeen;
    }

    public void setFirstSeen(Instant firstSeen) {
        this.firstSeen = firstSeen;
    }

    public Instant getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(Instant lastSeen) {
        this.lastSeen = lastSeen;
    }

    public Instant getRemovedAt() {
        return removedAt;
    }

    public void setRemovedAt(Instant removedAt) {
        this.removedAt = removedAt;
    }

    public String getChangeType() {
        return changeType;
    }

    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }

    public String getChangeFields() {
        return changeFields;
    }

    public void setChangeFields(String changeFields) {
        this.changeFields = changeFields;
    }

    public String getSuggestedBefore() {
        return suggestedBefore;
    }

    public void setSuggestedBefore(String suggestedBefore) {
        this.suggestedBefore = suggestedBefore;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(Instant changedAt) {
        this.changedAt = changedAt;
    }
}
