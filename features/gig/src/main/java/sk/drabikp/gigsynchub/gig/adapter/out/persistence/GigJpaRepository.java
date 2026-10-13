package sk.drabikp.gigsynchub.gig.adapter.out.persistence;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Spring Data JPA repository over {@link GigEntity}. Internal to the persistence adapter. */
interface GigJpaRepository extends JpaRepository<GigEntity, GigEntity.Key> {

    List<GigEntity> findByBandId(UUID bandId);

    /** The band's gigs among these ids — what a bulk action reads instead of one row at a time. */
    List<GigEntity> findByBandIdAndIdIn(UUID bandId, Collection<UUID> ids);

    /** The band's gig with this day and venue, off the unique index that keeps it the only one. */
    Optional<GigEntity> findByBandIdAndMatchKey(UUID bandId, String matchKey);

    /** Earliest first and bounded, off gig_band_show — what a public feed reads. */
    List<GigEntity> findByBandIdOrderByShowAtAscIdAsc(UUID bandId, Limit limit);

    List<GigEntity> findByBandIdAndStartDateTimeGreaterThanEqualAndStartDateTimeLessThan(
            UUID bandId, LocalDateTime fromInclusive, LocalDateTime toExclusive);

    /**
     * The gigs whose own span meets the range: starting before it ends, and ending (or, with no
     * end, starting) after it begins. A gig with no end is a one-day gig, so its start stands for
     * both — which is why {@code coalesce} is the predicate rather than a second query.
     */
    @Query("""
            select g from GigEntity g
            where g.bandId = :bandId
              and g.startDateTime < :toExclusive
              and coalesce(g.endDateTime, g.startDateTime) >= :fromInclusive
            """)
    List<GigEntity> coveringAnyOf(UUID bandId, LocalDateTime fromInclusive, LocalDateTime toExclusive);
}
