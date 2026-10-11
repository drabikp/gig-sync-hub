package sk.drabikp.bzscraper.gig.adapter.out.persistence;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sk.drabikp.bzscraper.gig.application.port.out.GigRepository;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.DateRange;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.gig.domain.GigQuery;
import sk.drabikp.bzscraper.gig.domain.GigSlice;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * JPA-backed {@link GigRepository}. Wraps the Spring Data repository and the
 * {@link GigEntityMapper}, keeping all persistence concerns out of the domain and
 * application layers. {@code save} upserts by gig identity (assigned id).
 */
@Component
@Transactional(readOnly = true)
class JpaGigRepository implements GigRepository {

    private final GigJpaRepository jpaRepository;
    private final GigEntityMapper mapper;
    private final GigPages pages;

    public JpaGigRepository(GigJpaRepository jpaRepository, GigEntityMapper mapper, GigPages pages) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.pages = pages;
    }

    @Override
    @Transactional
    public void save(BandId band, Gig gig) {
        GigEntity entity = mapper.toEntity(band, gig);
        // an update carries the version read in this transaction: if another transaction
        // saved the gig meanwhile, the commit fails instead of overwriting its change
        jpaRepository.findById(key(band, gig.id())).ifPresent(existing -> entity.setVersion(existing.getVersion()));
        jpaRepository.save(entity);
    }

    @Override
    public Optional<Gig> findById(BandId band, GigId id) {
        return jpaRepository.findById(key(band, id)).map(mapper::toDomain);
    }

    @Override
    public List<Gig> findAll(BandId band) {
        return jpaRepository.findByBandId(band.value()).stream().map(mapper::toDomain).toList();
    }

    @Override
    public GigSlice page(BandId band, GigQuery query, LocalDate today) {
        return pages.page(band, query, today);
    }

    @Override
    public List<Gig> findStartingWithin(BandId band, DateRange range) {
        LocalDateTime fromInclusive = range.start().atStartOfDay();
        LocalDateTime toExclusive = range.end().plusDays(1).atStartOfDay();
        return jpaRepository
                .findByBandIdAndStartDateTimeGreaterThanEqualAndStartDateTimeLessThan(band.value(), fromInclusive,
                        toExclusive)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional
    public void deleteById(BandId band, GigId id) {
        jpaRepository.deleteById(key(band, id));
    }

    private static GigEntity.Key key(BandId band, GigId id) {
        return new GigEntity.Key(band.value(), id.key());
    }
}
