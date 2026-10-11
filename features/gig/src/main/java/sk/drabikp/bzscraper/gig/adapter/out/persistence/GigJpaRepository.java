package sk.drabikp.bzscraper.gig.adapter.out.persistence;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Spring Data JPA repository over {@link GigEntity}. Internal to the persistence adapter. */
interface GigJpaRepository extends JpaRepository<GigEntity, GigEntity.Key> {

    List<GigEntity> findByBandId(UUID bandId);

    /** The band's gig with this day and venue, off the unique index that keeps it the only one. */
    Optional<GigEntity> findByBandIdAndMatchKey(UUID bandId, String matchKey);

    /** Earliest first and bounded, off gig_band_show — what a public feed reads. */
    List<GigEntity> findByBandIdOrderByShowAtAscIdAsc(UUID bandId, Limit limit);

    List<GigEntity> findByBandIdAndStartDateTimeGreaterThanEqualAndStartDateTimeLessThan(
            UUID bandId, LocalDateTime fromInclusive, LocalDateTime toExclusive);
}
