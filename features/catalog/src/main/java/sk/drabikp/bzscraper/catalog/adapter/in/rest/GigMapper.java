package sk.drabikp.bzscraper.catalog.adapter.in.rest;

import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import sk.drabikp.bzscraper.catalog.adapter.in.rest.api.GigDetailResponse;
import sk.drabikp.bzscraper.catalog.adapter.in.rest.api.GigEditedResponse;
import sk.drabikp.bzscraper.catalog.adapter.in.rest.api.GigHistoryResponse;
import sk.drabikp.bzscraper.catalog.adapter.in.rest.api.GigResponse;
import sk.drabikp.bzscraper.catalog.adapter.in.rest.api.PlatformResponse;
import sk.drabikp.bzscraper.catalog.adapter.in.rest.api.PlatformStateResponse;
import sk.drabikp.bzscraper.catalog.adapter.in.rest.api.QueueResultResponse;
import sk.drabikp.bzscraper.catalog.adapter.in.rest.api.QueuedTaskResponse;
import sk.drabikp.bzscraper.gig.api.GigFormMapper;
import sk.drabikp.bzscraper.gig.api.RefMapper;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.gig.domain.platform.PlatformTraits;
import sk.drabikp.bzscraper.sync.domain.QueueResult;
import sk.drabikp.bzscraper.sync.domain.SyncTask;

import java.util.List;

/** The catalog as the API has it (catalog.yaml): gigs with their platforms, history, queued work, platforms. */
@Mapper(uses = {GigFormMapper.class, RefMapper.class})
interface GigMapper {

    GigResponse toResponse(GigView view);

    List<GigResponse> toResponse(List<GigView> views);

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
