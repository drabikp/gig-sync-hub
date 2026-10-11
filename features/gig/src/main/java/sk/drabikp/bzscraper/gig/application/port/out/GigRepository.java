package sk.drabikp.bzscraper.gig.application.port.out;

import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.DateRange;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.gig.domain.GigQuery;
import sk.drabikp.bzscraper.gig.domain.GigSlice;

import java.time.LocalDate;

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

    /**
     * Every gig of the band. Only for work that is about ALL of them — an export, a reconciliation —
     * never to answer a page: {@link #page} does that, and it reads a page's worth of rows.
     */
    List<Gig> findAll(BandId band);

    /**
     * One page of the band's list: filtered, ordered and paged by the store. {@code today} is the day
     * the period is measured against, in the band's own wall clock (its gigs are stored in theirs).
     */
    GigSlice page(BandId band, GigQuery query, LocalDate today);

    List<Gig> findStartingWithin(BandId band, DateRange range);

    void deleteById(BandId band, GigId id);
}
