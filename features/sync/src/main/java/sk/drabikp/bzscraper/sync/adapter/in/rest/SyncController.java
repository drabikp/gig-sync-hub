package sk.drabikp.bzscraper.sync.adapter.in.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import sk.drabikp.bzscraper.gig.api.RefMapper;
import sk.drabikp.bzscraper.gig.domain.platform.Platforms;
import sk.drabikp.bzscraper.sync.adapter.in.rest.api.LogEntryResponse;
import sk.drabikp.bzscraper.sync.adapter.in.rest.api.SyncApi;
import sk.drabikp.bzscraper.sync.adapter.in.rest.api.SyncStatusResponse;
import sk.drabikp.bzscraper.sync.adapter.in.rest.api.TaskResponse;
import sk.drabikp.bzscraper.sync.application.port.in.PauseSyncUseCase;
import sk.drabikp.bzscraper.sync.application.port.in.PlatformBreakerUseCase;
import sk.drabikp.bzscraper.sync.application.port.in.SyncLogUseCase;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Serves a band's sync (sync.yaml). The use cases announce their changes on the live updates themselves.
 * A platform held back by its breaker is held back for every band (until accounts are per band).
 */
@RestController
class SyncController implements SyncApi {

    private final SyncLogUseCase syncLog;
    private final PauseSyncUseCase pause;
    private final PlatformBreakerUseCase breakers;
    private final Platforms platforms;
    private final Clock clock;
    private final SyncMapper mapper;
    private final RefMapper refs;

    SyncController(SyncLogUseCase syncLog, PauseSyncUseCase pause, PlatformBreakerUseCase breakers,
                   Platforms platforms, Clock clock, SyncMapper mapper, RefMapper refs) {
        this.syncLog = syncLog;
        this.pause = pause;
        this.breakers = breakers;
        this.platforms = platforms;
        this.clock = clock;
        this.mapper = mapper;
        this.refs = refs;
    }

    @Override
    public ResponseEntity<List<TaskResponse>> listTasks(UUID bandId, String show) {
        return ResponseEntity.ok(mapper.toResponse(syncLog.tasks(refs.toBandId(bandId), mapper.toShow(show))));
    }

    @Override
    public ResponseEntity<List<LogEntryResponse>> taskLog(UUID bandId, Long id) {
        return ResponseEntity.ok(mapper.toLogResponse(syncLog.log(refs.toBandId(bandId), id)));
    }

    @Override
    public ResponseEntity<Void> retryTask(UUID bandId, Long id) {
        syncLog.retry(refs.toBandId(bandId), id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> discardTask(UUID bandId, Long id) {
        syncLog.discard(refs.toBandId(bandId), id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<SyncStatusResponse> syncStatus(UUID bandId) {
        return ResponseEntity.ok(mapper.toResponse(pause.paused(refs.toBandId(bandId)), breakers.breakers(refs.toBandId(bandId)),
                syncLog.counts(refs.toBandId(bandId)), clock.instant()));
    }

    @Override
    public ResponseEntity<Void> pauseSync(UUID bandId) {
        pause.pause(refs.toBandId(bandId));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> resumeSync(UUID bandId) {
        pause.resume(refs.toBandId(bandId));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> resumePlatform(UUID bandId, String platform) {
        breakers.resume(refs.toBandId(bandId), platforms.get(platform));
        return ResponseEntity.noContent().build();
    }
}
