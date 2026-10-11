package sk.drabikp.bzscraper.gig.adapter.out.persistence;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.gig.domain.platform.Publication;

import java.util.List;
import java.util.UUID;

/** A gig's record on a platform ({@link PublishedGigEntity}) as the domain's {@link Publication} (MapStruct). */
@Mapper
interface PublicationEntityMapper {

    @Mapping(target = "platform", source = "key.platform")
    @Mapping(target = "gigId", source = "key.gigId")
    Publication toDomain(PublishedGigEntity entity);

    List<Publication> toDomain(List<PublishedGigEntity> entities);

    default Platform toPlatform(String id) {
        return Platform.of(id);
    }

    default GigId toGigId(UUID id) {
        return new GigId(id);
    }
}
