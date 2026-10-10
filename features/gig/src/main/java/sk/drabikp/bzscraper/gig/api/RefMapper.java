package sk.drabikp.bzscraper.gig.api;

import org.mapstruct.Mapper;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;

import java.util.UUID;

/**
 * How the API names a gig (its token, for its address), a platform (its id) and a band (its UUID) — used by every
 * feature's mapper ({@code uses = RefMapper.class}).
 */
@Mapper
public interface RefMapper {

    default String toToken(GigId id) {
        return id == null ? null : id.token();
    }

    default GigId toGigId(String token) {
        return token == null ? null : GigId.fromToken(token);
    }

    default String toId(Platform platform) {
        return platform == null ? null : platform.id();
    }

    /** A band as the API names it (its id, a UUID). */
    default BandId toBandId(UUID id) {
        return id == null ? null : new BandId(id);
    }

    default UUID toUuid(BandId band) {
        return band == null ? null : band.value();
    }
}
