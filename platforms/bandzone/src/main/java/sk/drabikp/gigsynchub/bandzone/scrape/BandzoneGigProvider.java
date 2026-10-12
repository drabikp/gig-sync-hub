package sk.drabikp.gigsynchub.bandzone.scrape;

import org.springframework.stereotype.Component;
import sk.drabikp.gigsynchub.bandzone.BandzonePortalException;

import java.time.Clock;
import java.util.List;

/** A band's gigs as its public Bandzone page lists them: fetched, then parsed. */
@Component
class BandzoneGigProvider implements GigProvider {

    private final BandzoneHtmlFetcher fetcher;
    private final BandzoneHtmlParser parser;

    public BandzoneGigProvider(BandzoneHtmlFetcher fetcher, Clock clock) {
        this.fetcher = fetcher;
        this.parser = new BandzoneHtmlParser(clock);
    }

    @Override
    public List<GigSummary> findByBand(String bandSlug) throws BandzonePortalException {
        return parser.parse(fetcher.fetchAllGigs(bandSlug));
    }
}
