package sk.drabikp.gigsynchub.catalog.adapter.in.rest;

import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.EnumMapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ValueMapping;
import sk.drabikp.gigsynchub.catalog.adapter.in.rest.api.GigDetailResponse;
import sk.drabikp.gigsynchub.catalog.adapter.in.rest.api.GigEditedResponse;
import sk.drabikp.gigsynchub.catalog.adapter.in.rest.api.GigHistoryResponse;
import sk.drabikp.gigsynchub.catalog.adapter.in.rest.api.GigListResponse;
import sk.drabikp.gigsynchub.catalog.adapter.in.rest.api.GigRowResponse;
import sk.drabikp.gigsynchub.catalog.adapter.in.rest.api.GigResponse;
import sk.drabikp.gigsynchub.catalog.adapter.in.rest.api.PlatformResponse;
import sk.drabikp.gigsynchub.catalog.adapter.in.rest.api.PlatformStateResponse;
import sk.drabikp.gigsynchub.catalog.adapter.in.rest.api.QueueResultResponse;
import sk.drabikp.gigsynchub.catalog.adapter.in.rest.api.QueuedTaskResponse;
import sk.drabikp.gigsynchub.gig.application.BadRequestException;
import sk.drabikp.gigsynchub.gig.domain.GigCursor;
import sk.drabikp.gigsynchub.gig.domain.GigQuery;
import sk.drabikp.gigsynchub.gig.domain.GigSlice;
import sk.drabikp.gigsynchub.gig.api.GigFormMapper;
import sk.drabikp.gigsynchub.gig.api.RefMapper;
import sk.drabikp.gigsynchub.gig.domain.GigId;
import sk.drabikp.gigsynchub.gig.domain.platform.Platform;
import sk.drabikp.gigsynchub.gig.domain.platform.PlatformTraits;
import sk.drabikp.gigsynchub.sync.domain.QueueResult;
import sk.drabikp.gigsynchub.sync.domain.SyncTask;

import java.time.format.DateTimeFormatter;
import java.util.List;

/** The catalog as the API has it (catalog.yaml): gigs with their platforms, history, queued work, platforms. */
@Mapper(uses = {GigFormMapper.class, RefMapper.class})
interface GigMapper {

    GigResponse toResponse(GigView view);

    List<GigResponse> toResponse(List<GigView> views);

    /** The page: its rows, how many gigs match in all, and where to read on from (absent at the end). */
    @Mapping(target = "gigs", source = "rows")
    @Mapping(target = "total", source = "slice.total")
    @Mapping(target = "cursor", expression = "java(slice.next() == null ? null : slice.next().encoded())")
    GigListResponse toResponse(GigSlice slice, List<GigRowView> rows);

    @Mapping(target = "id", source = "row.id")
    @Mapping(target = "date", expression = "java(view.row().start().toLocalDate())")
    @Mapping(target = "time", expression = "java(TIME.format(view.row().start()))")
    @Mapping(target = "title", source = "row.title")
    @Mapping(target = "venue", source = "row.venue")
    @Mapping(target = "city", source = "row.city")
    @Mapping(target = "cancelled", source = "row.cancelled")
    GigRowResponse toResponse(GigRowView view);

    /** A row's time as the page shows it, in the gig's own zone (the form's {@code time} reads the same). */
    DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * What the band asked of the list, as the store takes it: the period, the words, where to read on
     * from and how many rows. A cursor that is not one of ours is a bad request, not "from the top".
     */
    default GigQuery toQuery(String period, String words, String after, Integer size) {
        GigQuery query = GigQuery.upcoming().of(toPeriod(period)).matching(words);
        if (size != null) {
            query = query.sized(size);
        }
        try {
            GigCursor cursor = GigCursor.decode(after);
            return cursor == null ? query : query.from(cursor);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("That is not a page of this list — start again from the first one.");
        }
    }

    /** Which gigs the band asked to see; anything else is a {@link BadRequestException}. */
    @EnumMapping(unexpectedValueMappingException = BadRequestException.class)
    @ValueMapping(source = MappingConstants.ANY_REMAINING, target = MappingConstants.THROW_EXCEPTION)
    @ValueMapping(source = MappingConstants.NULL, target = "ALL")
    @ValueMapping(source = "all", target = "ALL")
    @ValueMapping(source = "needs", target = "NEEDS")
    @ValueMapping(source = "publishable", target = "PUBLISHABLE")
    GigViews.Show toShow(String show);

    /** Which side of today; nothing asked means what is to come. */
    @EnumMapping(unexpectedValueMappingException = BadRequestException.class)
    @ValueMapping(source = MappingConstants.ANY_REMAINING, target = MappingConstants.THROW_EXCEPTION)
    @ValueMapping(source = MappingConstants.NULL, target = "UPCOMING")
    @ValueMapping(source = "upcoming", target = "UPCOMING")
    @ValueMapping(source = "past", target = "PAST")
    GigQuery.Period toPeriod(String period);

    @Mapping(target = "taskId", source = "task.id")
    @Mapping(target = "action", source = "task.action")
    @Mapping(target = "attempts", source = "task.attempts", defaultValue = "0")
    @Mapping(target = "nextAttemptAt", source = "task.nextAttemptAt")
    @Mapping(target = "message", source = "task.message")
    PlatformStateResponse toResponse(GigView.OnPlatform onPlatform);

    @Mapping(target = "gig", source = "view")
    @Mapping(target = "history", source = "history")
    GigDetailResponse toResponse(GigView view, List<SyncTask> history);

    @Mapping(target = "taskId", source = "id")
    @Mapping(target = "at", source = "updatedAt")
    GigHistoryResponse toHistory(SyncTask task);

    @Mapping(target = "gig", source = "view")
    @Mapping(target = "queued", source = "queued")
    GigEditedResponse toResponse(GigView view, QueueResult queued);

    QueueResultResponse toResponse(QueueResult result);

    @Mapping(target = "taskId", source = "id")
    QueuedTaskResponse toQueued(SyncTask task);

    @Mapping(target = "id", source = "platform")
    @Mapping(target = "name", source = "displayName")
    @Mapping(target = "exportable", expression = "java(exportable.contains(traits.platform()))")
    PlatformResponse toResponse(PlatformTraits traits, @Context List<Platform> exportable);

    List<PlatformResponse> toResponse(List<PlatformTraits> traits, @Context List<Platform> exportable);

    /** The gigs a selection names (their tokens); none for an empty one. */
    List<GigId> toGigIds(List<String> tokens);
}
