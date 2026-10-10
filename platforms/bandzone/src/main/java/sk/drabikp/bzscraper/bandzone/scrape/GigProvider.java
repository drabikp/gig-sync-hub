package sk.drabikp.bzscraper.bandzone.scrape;

import sk.drabikp.bzscraper.bandzone.BandzonePortalException;

import java.util.List;

interface GigProvider {
    List<GigSummary> findByBand(String bandSlug) throws BandzonePortalException;
}
