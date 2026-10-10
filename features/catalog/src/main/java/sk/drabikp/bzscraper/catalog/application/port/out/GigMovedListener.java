package sk.drabikp.bzscraper.catalog.application.port.out;

import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.GigId;

/**
 * Told when an edit moves a gig's identity (its day or venue), inside the edit's transaction:
 * whatever refers to the gig by its id follows it — e.g. the band calendar's links. The catalog
 * doesn't know who listens.
 */
public interface GigMovedListener {

    void gigMoved(BandId band, GigId from, GigId to);
}
