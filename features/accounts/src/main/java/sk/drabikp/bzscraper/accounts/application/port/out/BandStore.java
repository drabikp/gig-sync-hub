package sk.drabikp.bzscraper.accounts.application.port.out;

import sk.drabikp.bzscraper.accounts.domain.Band;
import sk.drabikp.bzscraper.gig.domain.BandId;

import java.util.List;
import java.util.Optional;

public interface BandStore {

    Optional<Band> byId(BandId id);

    Optional<Band> bySlug(String slug);

    List<Band> all();

    void save(Band band);
}
