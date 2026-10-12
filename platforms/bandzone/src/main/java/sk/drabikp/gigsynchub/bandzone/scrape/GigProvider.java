package sk.drabikp.gigsynchub.bandzone.scrape;

import sk.drabikp.gigsynchub.bandzone.BandzonePortalException;

import java.util.List;

interface GigProvider {
    List<GigSummary> findByBand(String bandSlug) throws BandzonePortalException;
}
