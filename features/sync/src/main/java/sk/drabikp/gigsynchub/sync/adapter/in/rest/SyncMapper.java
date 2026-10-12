package sk.drabikp.gigsynchub.sync.adapter.in.rest;

import org.mapstruct.Context;
import org.mapstruct.EnumMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ValueMapping;
import sk.drabikp.gigsynchub.gig.api.RefMapper;
import sk.drabikp.gigsynchub.gig.application.BadRequestException;
import sk.drabikp.gigsynchub.sync.adapter.in.rest.api.BreakerResponse;
import sk.drabikp.gigsynchub.sync.adapter.in.rest.api.LogEntryResponse;
import sk.drabikp.gigsynchub.sync.adapter.in.rest.api.SyncStatusResponse;
import sk.drabikp.gigsynchub.sync.adapter.in.rest.api.TaskResponse;
import sk.drabikp.gigsynchub.sync.application.port.in.PlatformBreakerUseCase.Breaker;
import sk.drabikp.gigsynchub.sync.application.port.in.SyncLogUseCase;
import sk.drabikp.gigsynchub.sync.domain.StepType;
import sk.drabikp.gigsynchub.sync.domain.SyncCounts;
import sk.drabikp.gigsynchub.sync.domain.SyncLogEntry;
import sk.drabikp.gigsynchub.sync.domain.SyncTask;

import java.time.Instant;
import java.util.List;

/** The sync as the API sends it (sync.yaml). */
@Mapper(uses = RefMapper.class)
interface SyncMapper {

    /** {@code all} / {@code open} / {@code failed}; anything else is a {@link BadRequestException}. */
    @EnumMapping(unexpectedValueMappingException = BadRequestException.class)
    @ValueMapping(source = MappingConstants.ANY_REMAINING, target = MappingConstants.THROW_EXCEPTION)
    @ValueMapping(source = "all", target = "ALL")
    @ValueMapping(source = "open", target = "OPEN")
    @ValueMapping(source = "failed", target = "FAILED")
    SyncLogUseCase.Show toShow(String show);

    @Mapping(target = "retrying", expression = "java(task.retrying())")
    TaskResponse toResponse(SyncTask task);

    List<TaskResponse> toResponse(List<SyncTask> tasks);

    LogEntryResponse toResponse(SyncLogEntry entry);

    List<LogEntryResponse> toLogResponse(List<SyncLogEntry> log);

    @Mapping(target = "paused", source = "paused")
    @Mapping(target = "breakers", source = "breakers")
    @Mapping(target = "counts", source = "counts")
    SyncStatusResponse toResponse(boolean paused, List<Breaker> breakers, SyncCounts counts, @Context Instant now);

    @Mapping(target = "held", expression = "java(breaker.held(now))")
    BreakerResponse toResponse(Breaker breaker, @Context Instant now);

    /** Where a task's workflow is, as the user reads it ("bulk edit"). */
    default String toLabel(StepType step) {
        return step == null ? null : step.label();
    }
}
