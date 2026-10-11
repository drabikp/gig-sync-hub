package sk.drabikp.bzscraper.sync.adapter.out.persistence;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.sync.domain.SyncLogEntry;
import sk.drabikp.bzscraper.sync.domain.SyncTask;

import java.util.List;
import java.util.UUID;

/**
 * The outbox's rows as the domain's {@link SyncTask}s and {@link SyncLogEntry}s (MapStruct): the gig
 * by its key, the platform by its id, action, status and step by their names.
 */
@Mapper
interface SyncEntityMapper {

    @Mapping(target = "band", source = "bandId")
    SyncTask toTask(SyncTaskEntity entity);

    List<SyncTask> toTasks(List<SyncTaskEntity> entities);

    @Mapping(target = "at", source = "loggedAt")
    SyncLogEntry toEntry(SyncLogEntity entity);

    List<SyncLogEntry> toEntries(List<SyncLogEntity> entities);

    default Platform toPlatform(String id) {
        return Platform.of(id);
    }

    default BandId toBandId(UUID id) {
        return id == null ? null : new BandId(id);
    }

    default GigId toGigId(UUID id) {
        return new GigId(id);
    }
}
