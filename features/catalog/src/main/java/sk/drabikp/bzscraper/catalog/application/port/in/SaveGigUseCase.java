package sk.drabikp.bzscraper.catalog.application.port.in;

import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.Gig;

/**
 * Adds a new gig to the catalog (the source of truth). A gig never overwrites another: when the
 * catalog already has a gig with its identity (same day and venue), {@link GigIdentityTakenException}
 * — edit that one instead.
 */
public interface SaveGigUseCase {

    void add(BandId band, Gig gig);
}
