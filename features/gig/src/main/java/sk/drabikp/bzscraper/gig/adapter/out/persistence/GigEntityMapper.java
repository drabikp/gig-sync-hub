package sk.drabikp.bzscraper.gig.adapter.out.persistence;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import sk.drabikp.bzscraper.gig.domain.Address;
import sk.drabikp.bzscraper.gig.domain.Admission;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.GigSchedule;
import sk.drabikp.bzscraper.gig.domain.Location;
import sk.drabikp.bzscraper.gig.domain.Slot;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Translates between the {@link Gig} aggregate and its {@link GigEntity} (MapStruct). Schedule
 * crosses as wall-clock {@code LocalDateTime} plus country; the zone is rebuilt from the country on
 * the way back ({@link Location#zoneOf}). The lineup is one text, a band per line.
 */
@Mapper
interface GigEntityMapper {

    String LINEUP_DELIMITER = "\n";

    @Mapping(target = "bandId", source = "band.value")
    @Mapping(target = "id", expression = "java(gig.id().key())")
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "startDateTime", source = "gig.schedule.start")
    @Mapping(target = "endDateTime", source = "gig.schedule.end")
    @Mapping(target = "slotStart", source = "gig.schedule.slot.start")
    @Mapping(target = "slotEnd", source = "gig.schedule.slot.end")
    @Mapping(target = "venue", source = "gig.location.venue")
    @Mapping(target = "city", source = "gig.location.city")
    @Mapping(target = "country", source = "gig.location.country")
    @Mapping(target = "street", source = "gig.location.address.street")
    @Mapping(target = "postalCode", source = "gig.location.address.postalCode")
    @Mapping(target = "district", source = "gig.location.address.district")
    @Mapping(target = "region", source = "gig.location.address.region")
    @Mapping(target = "latitude", source = "gig.location.address.latitude")
    @Mapping(target = "longitude", source = "gig.location.address.longitude")
    @Mapping(target = "entryType", source = "gig.admission.type")
    @Mapping(target = "entryFee", source = "gig.admission.amount")
    GigEntity toEntity(BandId band, Gig gig);

    @Mapping(target = "schedule", source = ".")
    @Mapping(target = "location", source = ".")
    @Mapping(target = "admission", source = ".")
    @Mapping(target = "rescheduledTo", ignore = true)       // a method of Gig's that MapStruct takes for a setter
    Gig toDomain(GigEntity entity);

    default GigSchedule toSchedule(GigEntity e) {
        ZoneId zone = Location.zoneOf(e.getCountry());
        Slot slot = e.getSlotStart() == null ? null : new Slot(e.getSlotStart().atZone(zone), atZone(e.getSlotEnd(), zone));
        return new GigSchedule(e.getStartDateTime().atZone(zone), atZone(e.getEndDateTime(), zone), slot);
    }

    default Location toLocation(GigEntity e) {
        return new Location(e.getVenue(), e.getCity(), e.getCountry(), Address.ofParts(e.getStreet(), e.getPostalCode(),
                e.getDistrict(), e.getRegion(), e.getLatitude(), e.getLongitude()));
    }

    default Admission toAdmission(GigEntity e) {
        return Admission.of(e.getEntryType(), e.getEntryFee());
    }

    default LocalDateTime toLocal(ZonedDateTime time) {
        return time == null ? null : time.toLocalDateTime();
    }

    default String toLineup(List<String> lineup) {
        return String.join(LINEUP_DELIMITER, lineup);
    }

    default List<String> toLineup(String lineup) {
        return lineup == null || lineup.isBlank() ? List.of()
                : Arrays.stream(lineup.split(LINEUP_DELIMITER)).filter(s -> !s.isBlank()).toList();
    }

    private static ZonedDateTime atZone(LocalDateTime time, ZoneId zone) {
        return time == null ? null : time.atZone(zone);
    }
}
