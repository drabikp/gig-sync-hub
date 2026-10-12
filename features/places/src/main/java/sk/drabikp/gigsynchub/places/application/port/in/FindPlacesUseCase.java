package sk.drabikp.gigsynchub.places.application.port.in;

import sk.drabikp.gigsynchub.gig.domain.Country;
import sk.drabikp.gigsynchub.places.domain.Town;

import java.util.List;
import java.util.Optional;

/** Towns for the gig form (only fully known ones): suggestions while typing, and the town a known address means. */
public interface FindPlacesUseCase {

    List<Town> search(String text);

    /** The one town that fits (name, country, postal area), or empty when none or several do. */
    Optional<Town> resolve(String name, Country country, String postalCode);
}
