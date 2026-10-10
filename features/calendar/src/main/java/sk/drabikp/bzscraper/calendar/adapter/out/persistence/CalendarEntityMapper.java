package sk.drabikp.bzscraper.calendar.adapter.out.persistence;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import sk.drabikp.bzscraper.calendar.domain.CalendarChange;
import sk.drabikp.bzscraper.calendar.domain.KnownCalendarEvent;
import sk.drabikp.bzscraper.calendar.domain.event.CalendarEvent;
import sk.drabikp.bzscraper.calendar.domain.event.CalendarEventKind;
import sk.drabikp.bzscraper.calendar.domain.rules.ProfileRule;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The calendar's rows as its domain (MapStruct): band-profile rules, and the saved copy's events
 * with what changed in them (the changed fields stored as one text, by name, sorted).
 */
@Mapper
interface CalendarEntityMapper {

    @Mapping(target = "value", source = "ruleValue")
    ProfileRule toRule(CalendarRuleEntity entity);

    List<ProfileRule> toRules(List<CalendarRuleEntity> entities);

    /** Whose rule it is is the store's to set. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "bandId", ignore = true)
    @Mapping(target = "ruleValue", source = "value")
    CalendarRuleEntity toEntity(ProfileRule rule);

    List<CalendarRuleEntity> toEntities(List<ProfileRule> rules);

    @Mapping(target = "event", source = ".")
    @Mapping(target = "change", source = ".")
    KnownCalendarEvent toDomain(CalendarEventEntity entity);

    @Mapping(target = "id", source = "eventUid")
    @Mapping(target = "start", source = "startsAt")
    @Mapping(target = "end", source = "endsAt")
    CalendarEvent toEvent(CalendarEventEntity entity);

    /** Copies the event in; whose and which it is (band, {@code eventUid}) and when it was last seen stay the entity's. */
    @Mapping(target = "bandId", ignore = true)
    @Mapping(target = "eventUid", ignore = true)
    @Mapping(target = "lastSeen", ignore = true)
    @Mapping(target = "title", source = "event.title")
    @Mapping(target = "location", source = "event.location")
    @Mapping(target = "notes", source = "event.notes")
    @Mapping(target = "startsAt", source = "event.start")
    @Mapping(target = "endsAt", source = "event.end")
    @Mapping(target = "allDay", source = "event.allDay")
    @Mapping(target = "repeating", source = "event.repeating")
    @Mapping(target = "shownAsFree", source = "event.shownAsFree")
    @Mapping(target = "calendarStatus", source = "event.calendarStatus")
    @Mapping(target = "lastModified", source = "event.lastModified")
    @Mapping(target = "changeType", source = "change.type")
    @Mapping(target = "changeFields", source = "change.fields")
    @Mapping(target = "suggestedBefore", source = "change.suggestedBefore")
    @Mapping(target = "changedAt", source = "change.at")
    void update(KnownCalendarEvent known, @MappingTarget CalendarEventEntity entity);

    /** The change the entity keeps; null when there is none. */
    default CalendarChange toChange(CalendarEventEntity entity) {
        return entity.getChangeType() == null ? null : new CalendarChange(
                CalendarChange.Type.valueOf(entity.getChangeType()), toFields(entity.getChangeFields()),
                toKind(entity.getSuggestedBefore()), entity.getChangedAt());
    }

    CalendarEventKind toKind(String name);

    default String toText(Set<CalendarChange.Field> fields) {
        return fields == null ? null : fields.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }

    default Set<CalendarChange.Field> toFields(String text) {
        return text == null || text.isEmpty() ? Set.of()
                : Arrays.stream(text.split(",")).map(CalendarChange.Field::valueOf).collect(Collectors.toSet());
    }
}
