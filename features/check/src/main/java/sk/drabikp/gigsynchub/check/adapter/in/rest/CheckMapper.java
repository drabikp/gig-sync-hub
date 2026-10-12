package sk.drabikp.gigsynchub.check.adapter.in.rest;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueMappingStrategy;
import sk.drabikp.gigsynchub.check.adapter.in.rest.api.CheckResultResponse;
import sk.drabikp.gigsynchub.check.adapter.in.rest.api.CheckStateResponse;
import sk.drabikp.gigsynchub.check.adapter.in.rest.api.DriftResponse;
import sk.drabikp.gigsynchub.check.domain.Drift;
import sk.drabikp.gigsynchub.check.domain.PlatformCheck;
import sk.drabikp.gigsynchub.gig.api.RefMapper;
import sk.drabikp.gigsynchub.gig.domain.platform.Platform;

import java.util.Map;
import java.util.TreeMap;

/** The platform check as the API sends it (check.yaml). */
@Mapper(uses = RefMapper.class)
interface CheckMapper {

    /** Whether a check runs, and the latest result (null before the first — the answer is still there). */
    @BeanMapping(nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
    @Mapping(target = "running", source = "running")
    @Mapping(target = "last", source = "last")
    CheckStateResponse toResponse(boolean running, PlatformCheck last);

    CheckResultResponse toResponse(PlatformCheck result);

    DriftResponse toResponse(Drift drift);

    /** Per platform that couldn't be read, why — by the platform's id. */
    default Map<String, String> toUnreadable(Map<Platform, String> unreadable) {
        Map<String, String> byId = new TreeMap<>();
        unreadable.forEach((platform, why) -> byId.put(platform.id(), why));
        return byId;
    }

    /** Per platform, how many of its events no gig is published as — by the platform's id. */
    default Map<String, Integer> toUnlinked(Map<Platform, Integer> unlinked) {
        Map<String, Integer> byId = new TreeMap<>();
        unlinked.forEach((platform, count) -> byId.put(platform.id(), count));
        return byId;
    }
}
