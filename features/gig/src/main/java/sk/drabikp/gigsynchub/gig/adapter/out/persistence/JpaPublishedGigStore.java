package sk.drabikp.gigsynchub.gig.adapter.out.persistence;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sk.drabikp.gigsynchub.gig.application.port.out.PublishedGigStore;
import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.gig.domain.GigId;
import sk.drabikp.gigsynchub.gig.domain.platform.Platform;
import sk.drabikp.gigsynchub.gig.domain.platform.Publication;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * JPA-backed {@link PublishedGigStore}, in the same database as the gig catalog, so a
 * catalog change and the matching record change can commit together (see
 * {@link sk.drabikp.gigsynchub.gig.application.port.out.Transactions}).
 */
@Component
@Transactional(readOnly = true)
class JpaPublishedGigStore implements PublishedGigStore {

    private final PublishedGigJpaRepository jpaRepository;
    private final PublicationEntityMapper mapper;

    public JpaPublishedGigStore(PublishedGigJpaRepository jpaRepository, PublicationEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public boolean isPublished(BandId band, Platform platform, GigId gigId) {
        return jpaRepository.existsById(key(band, platform, gigId));
    }

    @Override
    public Optional<String> externalRef(BandId band, Platform platform, GigId gigId) {
        return jpaRepository.findById(key(band, platform, gigId))
                .map(PublishedGigEntity::getExternalRef)
                .filter(ref -> !ref.isBlank());
    }

    @Override
    @Transactional
    public void record(BandId band, Platform platform, GigId gigId, String externalRef) {
        jpaRepository.save(new PublishedGigEntity(key(band, platform, gigId), externalRef));
    }

    @Override
    @Transactional
    public void remove(BandId band, Platform platform, GigId gigId) {
        jpaRepository.deleteById(key(band, platform, gigId));
    }

    @Override
    public List<Publication> of(BandId band, Set<GigId> gigIds) {
        if (gigIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository
                .findByKeyBandIdAndKeyGigIdIn(band.value(), gigIds.stream().map(GigId::value).toList())
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Publication> all(BandId band) {
        return mapper.toDomain(jpaRepository.findByKeyBandId(band.value()));
    }

    private static PublishedGigEntity.Key key(BandId band, Platform platform, GigId gigId) {
        return new PublishedGigEntity.Key(band.value(), platform.id(), gigId.value());
    }
}
