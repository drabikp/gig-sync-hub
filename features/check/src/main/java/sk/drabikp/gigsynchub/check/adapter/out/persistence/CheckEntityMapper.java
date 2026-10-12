package sk.drabikp.gigsynchub.check.adapter.out.persistence;

import org.mapstruct.Mapper;
import sk.drabikp.gigsynchub.check.domain.Drift;
import sk.drabikp.gigsynchub.check.domain.PlatformCheck;
import sk.drabikp.gigsynchub.gig.domain.GigId;
import sk.drabikp.gigsynchub.gig.domain.platform.Platform;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A platform check as the row keeps it (MapStruct): plain records written as JSON — a gig by its key, a
 * platform by its id.
 */
@Mapper
interface CheckEntityMapper {

    /** The check as JSON holds it. */
    record StoredCheck(Instant checkedAt, List<StoredDrift> drifts, Map<String, String> unreadable,
                       Map<String, Integer> unlinked) {
    }

    record StoredDrift(String platform, String gigId, String gigLabel, String externalRef, Drift.Kind kind,
                       List<StoredDifference> differences) {
    }

    record StoredDifference(Drift.Field field, String catalog, String platform, String text) {
    }

    StoredCheck toStored(PlatformCheck check);

    PlatformCheck toCheck(StoredCheck stored);

    StoredDrift toStored(Drift drift);

    Drift toDrift(StoredDrift stored);

    StoredDifference toStored(Drift.Difference difference);

    Drift.Difference toDifference(StoredDifference stored);

    default String toId(Platform platform) {
        return platform == null ? null : platform.id();
    }

    default Platform toPlatform(String id) {
        return id == null ? null : Platform.of(id);
    }

    default String toKey(GigId gigId) {
        return gigId == null ? null : gigId.key();
    }

    default GigId toGigId(String key) {
        return key == null ? null : GigId.fromKey(key);
    }

    default Map<String, String> toStoredText(Map<Platform, String> byPlatform) {
        Map<String, String> stored = new TreeMap<>();
        byPlatform.forEach((platform, text) -> stored.put(platform.id(), text));
        return stored;
    }

    default Map<Platform, String> toText(Map<String, String> stored) {
        Map<Platform, String> byPlatform = new TreeMap<>();
        stored.forEach((id, text) -> byPlatform.put(Platform.of(id), text));
        return byPlatform;
    }

    default Map<String, Integer> toStoredCounts(Map<Platform, Integer> byPlatform) {
        Map<String, Integer> stored = new TreeMap<>();
        byPlatform.forEach((platform, count) -> stored.put(platform.id(), count));
        return stored;
    }

    default Map<Platform, Integer> toCounts(Map<String, Integer> stored) {
        Map<Platform, Integer> byPlatform = new TreeMap<>();
        stored.forEach((id, count) -> byPlatform.put(Platform.of(id), count));
        return byPlatform;
    }
}
