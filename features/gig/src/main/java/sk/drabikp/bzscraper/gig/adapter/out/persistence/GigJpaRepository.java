package sk.drabikp.bzscraper.gig.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Spring Data JPA repository over {@link GigEntity}. Internal to the persistence adapter. */
interface GigJpaRepository extends JpaRepository<GigEntity, GigEntity.Key> {

    List<GigEntity> findByBandId(UUID bandId);

    List<GigEntity> findByBandIdAndStartDateTimeGreaterThanEqualAndStartDateTimeLessThan(
            UUID bandId, LocalDateTime fromInclusive, LocalDateTime toExclusive);
}
