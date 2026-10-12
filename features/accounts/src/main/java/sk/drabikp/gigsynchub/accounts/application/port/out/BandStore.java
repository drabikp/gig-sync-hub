package sk.drabikp.gigsynchub.accounts.application.port.out;

import org.jmolecules.ddd.annotation.Repository;
import sk.drabikp.gigsynchub.accounts.domain.Band;
import sk.drabikp.gigsynchub.gig.domain.BandId;

import java.util.List;
import java.util.Optional;

@Repository
public interface BandStore {

    Optional<Band> byId(BandId id);

    Optional<Band> bySlug(String slug);

    List<Band> all();

    void save(Band band);

    /** Deletes the band and everything it has (its gigs, work, calendar, accounts — the database's cascades). */
    void delete(BandId band);
}
