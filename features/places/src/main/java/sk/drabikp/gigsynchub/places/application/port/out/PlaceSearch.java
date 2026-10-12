package sk.drabikp.gigsynchub.places.application.port.out;

import sk.drabikp.gigsynchub.places.domain.Town;

import java.util.List;

/**
 * Finds towns by name — with their district, region, postal code and coordinates, so
 * same-named towns can be told apart. Czech and Slovak towns only (the band's markets).
 * Never throws: an unreachable search gives no suggestions, and the user types the town.
 */
public interface PlaceSearch {

    List<Town> towns(String text);
}
