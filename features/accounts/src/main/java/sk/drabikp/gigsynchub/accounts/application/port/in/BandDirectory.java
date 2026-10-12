package sk.drabikp.gigsynchub.accounts.application.port.in;

import sk.drabikp.gigsynchub.accounts.domain.Band;
import sk.drabikp.gigsynchub.gig.domain.BandId;

import java.util.List;
import java.util.Optional;

/** The bands the app has — for work done for every band (the nightly check) and public addresses. */
public interface BandDirectory {

    List<BandId> all();

    Optional<Band> bySlug(String slug);
}
