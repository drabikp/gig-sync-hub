package sk.drabikp.bzscraper.gig.application.port.out;

import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.DateRange;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.GigId;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for the local gig store — the source of truth, one catalog per band. Speaks only
 * the domain ({@link Gig} / {@link GigId}, the band's {@link BandId}); the persistence technology
 * lives entirely behind the implementing adapter. {@code save} is an upsert keyed by the band and
 * the {@link GigId} — the same day and venue in two bands are two gigs.
 */
public interface GigRepository {

    void save(BandId band, Gig gig);

    Optional<Gig> findById(BandId band, GigId id);

    List<Gig> findAll(BandId band);

    List<Gig> findStartingWithin(BandId band, DateRange range);

    void deleteById(BandId band, GigId id);
}
