package sk.drabikp.bzscraper.bandzone.scrape;

import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.accounts.application.port.in.PlatformAccounts;
import sk.drabikp.bzscraper.bandzone.BandzoneAccount;
import sk.drabikp.bzscraper.bandzone.BandzonePlatform;
import sk.drabikp.bzscraper.bandzone.BandzonePortalException;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.importing.application.port.out.GigImporter;
import sk.drabikp.bzscraper.importing.domain.ImportedGig;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Imports a band's Bandzone gigs (planned and played) by scraping its public page (the address its
 * connected account names). Each keeps its Bandzone concert id — the same id publishing records — so
 * importing links the catalog gig to the existing concert. Scraped gigs that can't form
 * a valid gig (no start/city) are dropped at the mapping boundary.
 */
@Component
class BandzoneGigImporter implements GigImporter {

    private final GigProvider gigProvider;
    private final PlatformAccounts accounts;

    public BandzoneGigImporter(GigProvider gigProvider, PlatformAccounts accounts) {
        this.gigProvider = gigProvider;
        this.accounts = accounts;
    }

    @Override
    public Platform platform() {
        return BandzonePlatform.PLATFORM;
    }

    @Override
    public List<ImportedGig> importGigs(BandId band) throws BandzonePortalException {
        String bandSlug = BandzoneAccount.of(accounts, band).bandSlug();
        List<ImportedGig> gigs = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (GigSummary summary : gigProvider.findByBand(bandSlug)) {
            if (summary.bzId() == null || summary.bzId().isBlank() || !seen.add(summary.bzId())) {
                continue;
            }
            GigSummaryToGigMapper.toGig(summary)
                    .ifPresent(gig -> gigs.add(new ImportedGig(BandzonePlatform.PLATFORM, gig, summary.bzId())));
        }
        return gigs;
    }
}
