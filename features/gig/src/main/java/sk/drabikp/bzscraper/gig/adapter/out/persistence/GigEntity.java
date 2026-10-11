package sk.drabikp.bzscraper.gig.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import sk.drabikp.bzscraper.gig.domain.Country;
import sk.drabikp.bzscraper.gig.domain.EntryType;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA persistence model for a gig — this is the ONLY place JPA annotations live.
 * It is a flattened mirror of the {@link sk.drabikp.bzscraper.gig.domain.Gig}
 * aggregate; {@link GigEntityMapper} (MapStruct) translates between the two. The domain never
 * imports this class.
 *
 * Schedule is stored as wall-clock {@code LocalDateTime} plus the country; the
 * zone is reconstructed from the country on the way out, sidestepping Hibernate's
 * {@code ZonedDateTime} timezone handling. {@code id} is the gig's own identity within its band
 * ({@code band_id}), so {@code save} upserts by the band and that identity; {@code matchKey} is
 * the day and the venue ({@code GigMatch.key()}), written with the row so the store can answer
 * "which gig is this copy about" and refuse a second gig with the same one
 * ({@code unique (band_id, match_key)}).
 */
@Entity
@Table(name = "gig")
@IdClass(GigEntity.Key.class)
class GigEntity {

    /** A gig's key: its band and its identity there ({@code GigId.value()}). */
    static final class Key implements Serializable {

        private UUID bandId;
        private UUID id;

        Key() {
            // for JPA
        }

        Key(UUID bandId, UUID id) {
            this.bandId = bandId;
            this.id = id;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(bandId, k.bandId) && Objects.equals(id, k.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(bandId, id);
        }
    }

    @Id
    private UUID bandId;
    @Id
    private UUID id;
    /** The day and the venue ({@code GigMatch.key()}): not identity, but what a copy is matched by. */
    private String matchKey;
    /** Optimistic lock: bumped on every update; a save based on an older one fails. */
    @Version
    private Long version;
    private String title;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    /** The band's own slot within the event (null when not given). */
    private LocalDateTime slotStart;
    private LocalDateTime slotEnd;
    private String venue;
    private String city;
    /** The town's district etc. when it was picked from the place search (all may be null). */
    private String street;
    private String postalCode;
    private String district;
    private String region;
    private Double latitude;
    private Double longitude;
    @Enumerated(EnumType.STRING)
    private Country country;
    @Column(length = 2000)
    private String lineup;
    @Enumerated(EnumType.STRING)
    private EntryType entryType;
    private String entryFee;
    @Column(length = 4000)
    private String description;
    private String facebookUrl;
    private String ticketUrl;
    private String posterImageUrl;
    private boolean cancelled;
    /**
     * When the band plays — the slot if the event has one, else the start. The DATABASE keeps it
     * (a generated column), so no writer can forget it and the list's index always agrees with the
     * rows; here it is read-only, and it is what the list orders and pages by.
     */
    @Column(insertable = false, updatable = false)
    private LocalDateTime showAt;
    /**
     * Title, venue and town folded as {@link sk.drabikp.bzscraper.gig.domain.TextFold} folds them, so
     * the list's search happens in the database and "kosice" finds "Košice". Written with every save.
     */
    @Column(length = 800, nullable = false)
    private String searchText = "";

    public GigEntity() {
        // for JPA and GigEntityMapper
    }

    public UUID getBandId() {
        return bandId;
    }

    public void setBandId(UUID bandId) {
        this.bandId = bandId;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getMatchKey() {
        return matchKey;
    }

    public void setMatchKey(String matchKey) {
        this.matchKey = matchKey;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDateTime getStartDateTime() {
        return startDateTime;
    }

    public void setStartDateTime(LocalDateTime startDateTime) {
        this.startDateTime = startDateTime;
    }

    public LocalDateTime getEndDateTime() {
        return endDateTime;
    }

    public void setEndDateTime(LocalDateTime endDateTime) {
        this.endDateTime = endDateTime;
    }

    public LocalDateTime getSlotStart() {
        return slotStart;
    }

    public void setSlotStart(LocalDateTime slotStart) {
        this.slotStart = slotStart;
    }

    public LocalDateTime getSlotEnd() {
        return slotEnd;
    }

    public void setSlotEnd(LocalDateTime slotEnd) {
        this.slotEnd = slotEnd;
    }

    public String getVenue() {
        return venue;
    }

    public void setVenue(String venue) {
        this.venue = venue;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Country getCountry() {
        return country;
    }

    public void setCountry(Country country) {
        this.country = country;
    }

    public String getLineup() {
        return lineup;
    }

    public void setLineup(String lineup) {
        this.lineup = lineup;
    }

    public EntryType getEntryType() {
        return entryType;
    }

    public void setEntryType(EntryType entryType) {
        this.entryType = entryType;
    }

    public String getEntryFee() {
        return entryFee;
    }

    public void setEntryFee(String entryFee) {
        this.entryFee = entryFee;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getFacebookUrl() {
        return facebookUrl;
    }

    public void setFacebookUrl(String facebookUrl) {
        this.facebookUrl = facebookUrl;
    }

    public String getTicketUrl() {
        return ticketUrl;
    }

    public void setTicketUrl(String ticketUrl) {
        this.ticketUrl = ticketUrl;
    }

    public String getPosterImageUrl() {
        return posterImageUrl;
    }

    public void setPosterImageUrl(String posterImageUrl) {
        this.posterImageUrl = posterImageUrl;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public LocalDateTime getShowAt() {
        return showAt;
    }

    public String getSearchText() {
        return searchText;
    }

    public void setSearchText(String searchText) {
        this.searchText = searchText == null ? "" : searchText;
    }
}
