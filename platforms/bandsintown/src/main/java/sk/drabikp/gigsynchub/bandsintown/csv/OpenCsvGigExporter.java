package sk.drabikp.gigsynchub.bandsintown.csv;

import org.springframework.stereotype.Component;
import sk.drabikp.gigsynchub.accounts.application.port.in.PlatformAccounts;
import sk.drabikp.gigsynchub.bandsintown.BandsintownAccount;
import sk.drabikp.gigsynchub.bandsintown.BandsintownPlatform;
import sk.drabikp.gigsynchub.catalog.application.port.out.GigExporter;
import sk.drabikp.gigsynchub.gig.application.GigErrorCode;
import sk.drabikp.gigsynchub.gig.application.UserFacingException;
import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.gig.domain.Gig;
import sk.drabikp.gigsynchub.gig.domain.platform.Platform;

import java.util.List;
import java.util.Map;

/**
 * Exports gigs as Bandsintown's 28-column bulk-import template (see
 * {@link BandsintownCsv}), for a manual import. Created gigs carry no Event Id — the
 * importer assigns one.
 */
@Component
class OpenCsvGigExporter implements GigExporter {

    private final PlatformAccounts accounts;

    public OpenCsvGigExporter(PlatformAccounts accounts) {
        this.accounts = accounts;
    }

    @Override
    public Platform platform() {
        return BandsintownPlatform.PLATFORM;
    }

    @Override
    public String fileName() {
        return "bandsintown-gigs.csv";
    }

    @Override
    public String mediaType() {
        return "text/csv";
    }

    @Override
    public String export(BandId band, List<Gig> gigs) {
        String artistName = accounts.of(band, BandsintownPlatform.PLATFORM)
                .map(account -> account.setting(BandsintownAccount.ARTIST_NAME)).orElse("");
        if (artistName.isBlank()) {
            throw new UserFacingException(GigErrorCode.NOT_CONFIGURED, Map.of("setting", "Bandsintown"),
                    "Connect Bandsintown in Settings (the artist as Bandsintown spells it) — every row of the file "
                    + "carries it.");
        }
        return BandsintownCsv.template(gigs, artistName);
    }
}
