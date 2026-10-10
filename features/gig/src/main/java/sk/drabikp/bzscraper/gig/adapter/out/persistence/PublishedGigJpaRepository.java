package sk.drabikp.bzscraper.gig.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Spring Data repository for {@link PublishedGigEntity}; used only by {@link JpaPublishedGigStore}. */
interface PublishedGigJpaRepository extends JpaRepository<PublishedGigEntity, PublishedGigEntity.Key> {

    List<PublishedGigEntity> findByKeyBandIdAndKeyGigId(UUID bandId, String gigId);

    List<PublishedGigEntity> findByKeyBandId(UUID bandId);
}
