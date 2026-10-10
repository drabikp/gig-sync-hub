package sk.drabikp.bzscraper.calendar.adapter.in.rest;

import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import sk.drabikp.bzscraper.calendar.adapter.in.rest.api.CalendarChangeFieldValue;
import sk.drabikp.bzscraper.calendar.adapter.in.rest.api.CalendarConnectionResponse;
import sk.drabikp.bzscraper.calendar.adapter.in.rest.api.CalendarFilterValue;
import sk.drabikp.bzscraper.calendar.adapter.in.rest.api.CalendarKindValue;
import sk.drabikp.bzscraper.calendar.adapter.in.rest.api.CalendarOverviewResponse;
import sk.drabikp.bzscraper.calendar.adapter.in.rest.api.CalendarRowResponse;
import sk.drabikp.bzscraper.calendar.adapter.in.rest.api.GigRefResponse;
import sk.drabikp.bzscraper.calendar.adapter.in.rest.api.ReasonResponse;
import sk.drabikp.bzscraper.calendar.adapter.in.rest.api.RuleKindResponse;
import sk.drabikp.bzscraper.calendar.adapter.in.rest.api.RuleRequest;
import sk.drabikp.bzscraper.calendar.adapter.in.rest.api.RuleResponse;
import sk.drabikp.bzscraper.calendar.adapter.in.rest.api.RuleValueValue;
import sk.drabikp.bzscraper.calendar.application.port.in.CalendarSetupUseCase.Connection;
import sk.drabikp.bzscraper.calendar.application.port.in.CalendarSetupUseCase.RuleDraft;
import sk.drabikp.bzscraper.calendar.domain.CalendarChange;
import sk.drabikp.bzscraper.calendar.domain.CalendarFilter;
import sk.drabikp.bzscraper.calendar.domain.CalendarOverview;
import sk.drabikp.bzscraper.calendar.domain.CalendarRow;
import sk.drabikp.bzscraper.calendar.domain.event.CalendarEventKind;
import sk.drabikp.bzscraper.calendar.domain.rules.CalendarClassification;
import sk.drabikp.bzscraper.calendar.domain.rules.ProfileRule;
import sk.drabikp.bzscraper.calendar.domain.rules.RuleKind;
import sk.drabikp.bzscraper.gig.api.GigFormMapper;
import sk.drabikp.bzscraper.gig.api.RefMapper;
import sk.drabikp.bzscraper.gig.domain.Gig;

import java.util.List;
import java.util.Set;

/**
 * The band calendar as the API sends it (calendar.yaml). The overview is ready as the domain made
 * it (rows latest first, the views each is in, what link-same-day takes); this only maps it.
 */
@Mapper(uses = {GigFormMapper.class, RefMapper.class})
interface CalendarMapper {

    /** The calendar as read. */
    default CalendarOverviewResponse toResponse(CalendarOverview overview) {
        return toResponse(overview, overview);
    }

    /** No calendar configured: nothing to show. */
    default CalendarOverviewResponse unconfigured() {
        return new CalendarOverviewResponse(false, null, null, List.of());
    }

    /** {@code views}: the same overview, which each row's views and link-same-day are looked up in. */
    @Mapping(target = "configured", constant = "true")
    CalendarOverviewResponse toResponse(CalendarOverview overview, @Context CalendarOverview views);

    @Mapping(target = "eventId", source = "classification.event.id")
    @Mapping(target = "title", source = "classification.event.title")
    @Mapping(target = "location", source = "classification.event.location")
    @Mapping(target = "notes", source = "classification.event.notes")
    @Mapping(target = "start", expression = "java(row.event().start().toString())")
    @Mapping(target = "end", expression = "java(row.event().end().toString())")
    @Mapping(target = "allDay", source = "classification.event.allDay")
    @Mapping(target = "kind", source = "classification.kind")
    @Mapping(target = "suggested", source = "classification.suggested")
    @Mapping(target = "decidedByUser", source = "classification.decidedByUser")
    @Mapping(target = "status", source = "classification.status")
    @Mapping(target = "score", source = "classification.score")
    @Mapping(target = "reasons", source = "classification.reasons")
    @Mapping(target = "missingFromCatalog", expression = "java(row.missingFromCatalog())")
    @Mapping(target = "needsAttention", expression = "java(row.needsAttention())")
    @Mapping(target = "linkable", expression = "java(views.linkable(row))")
    @Mapping(target = "filters", expression = "java(toFilterValues(views.filters(row)))")
    CalendarRowResponse toResponse(CalendarRow row, @Context CalendarOverview views);

    @Mapping(target = "text", expression = "java(reason.text())")
    ReasonResponse toResponse(CalendarClassification.Reason reason);

    @Mapping(target = "id", expression = "java(gig.id().token())")
    @Mapping(target = "start", expression = "java(gig.schedule().start().toLocalDateTime().toString())")
    @Mapping(target = "city", source = "location.city")
    GigRefResponse toRef(Gig gig);

    @Mapping(target = "weighted", expression = "java(rule.kind().weighted())")
    RuleResponse toResponse(ProfileRule rule);

    List<RuleResponse> toResponse(List<ProfileRule> rules);

    CalendarEventKind toKind(CalendarKindValue kind);

    RuleDraft toDraft(RuleRequest rule);

    List<RuleDraft> toDrafts(List<RuleRequest> rules);

    @Mapping(target = "kind", expression = "java(kind.name())")
    @Mapping(target = "weighted", expression = "java(kind.weighted())")
    @Mapping(target = "value", expression = "java(toValue(kind.value()))")
    RuleKindResponse toKindResponse(RuleKind kind);

    RuleValueValue toValue(RuleKind.Value value);

    List<RuleKindResponse> toKindResponses(List<RuleKind> kinds);

    CalendarConnectionResponse toResponse(Connection connection);

    /** The fields an event changed in, by name. */
    default List<CalendarChangeFieldValue> toFields(Set<CalendarChange.Field> fields) {
        return fields.stream().map(Enum::name).sorted().map(CalendarChangeFieldValue::fromValue).toList();
    }

    List<CalendarFilterValue> toFilterValues(List<CalendarFilter> filters);
}
