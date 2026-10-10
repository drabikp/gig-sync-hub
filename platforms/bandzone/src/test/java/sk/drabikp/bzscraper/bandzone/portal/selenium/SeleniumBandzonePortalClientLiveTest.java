package sk.drabikp.bzscraper.bandzone.portal.selenium;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import sk.drabikp.bzscraper.accounts.application.AccountsFakes;
import sk.drabikp.bzscraper.bandzone.BandzonePlatform;
import sk.drabikp.bzscraper.bandzone.BandzoneProperties;
import sk.drabikp.bzscraper.bandzone.portal.BandzoneSession;
import sk.drabikp.bzscraper.browser.BrowserPool;
import sk.drabikp.bzscraper.browser.BrowserProperties;
import sk.drabikp.bzscraper.browser.SeleniumOptions;
import sk.drabikp.bzscraper.gig.domain.Address;
import sk.drabikp.bzscraper.gig.domain.Admission;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.Country;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.GigSchedule;
import sk.drabikp.bzscraper.gig.domain.Location;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Live integration test — drives real Bandzone with a real browser: CREATES a gig on
 * the configured band with every field Bandzone has (end, club venue, poster, paid entry,
 * lineup with a profile band and a profile-less one), EDITS it (free-text venue, other
 * city, a performer removed), CANCELS it (checked on the band's page), then DELETES it (checked;
 * cleanup runs even when a step fails) and checks that deleting it again finds it gone.
 * Skipped unless {@code BZ_LIVE=true}. Reads creds from env so nothing is committed:
 *   BZ_LOGIN, BZ_PASSWORD, BZ_SLUG  (required)
 *   BZ_LINEUP_BAND  a Bandzone band that may be notified (default: none, lineup keeps stubs only)
 *   BZ_KEEP=true    keep the gig for manual inspection (prints its id)
 *   BZ_CHROMIUM (default /usr/bin/chromium), BZ_CHROMEDRIVER (default /usr/bin/chromedriver)
 *
 * Use a test band — the gig is briefly public, and BZ_LINEUP_BAND gets notified.
 */
@EnabledIfEnvironmentVariable(named = "BZ_LIVE", matches = "true")
class SeleniumBandzonePortalClientLiveTest {

    private static final ZoneId PRAGUE = ZoneId.of("Europe/Prague");
    private static final String STUB = "Bzscraper Testovaci Interpret";

    private static String env(String name, String fallback) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? fallback : v;
    }

    @Test
    void creates_edits_and_deletes_a_gig_with_every_field() {
        SeleniumBandzonePortalClient client = new SeleniumBandzonePortalClient(
                new BandzoneProperties(env("BZ_BASE_URL", "https://bandzone.cz"), null, null, null,
                        new SeleniumOptions(true, Boolean.parseBoolean(env("BZ_HEADLESS", "true")),
                                env("BZ_CHROMIUM", "/usr/bin/chromium"), env("BZ_CHROMEDRIVER", "/usr/bin/chromedriver"),
                                env("BZ_PROFILE", ""), null)),
                new BrowserProperties(env("BZ_PASSWORD_STORE", "auto"), null, null, null),
                new AccountsFakes.Accounts().with(BandId.FIRST, BandzonePlatform.PLATFORM,
                        Map.of("login", env("BZ_LOGIN", ""), "bandSlug", env("BZ_SLUG", "")),
                        Map.of("password", env("BZ_PASSWORD", ""))), new BrowserPool(1));
        String lineupBand = env("BZ_LINEUP_BAND", "");
        boolean keep = Boolean.parseBoolean(env("BZ_KEEP", "false"));

        List<String> fullLineup = lineupBand.isBlank() ? List.of(STUB) : List.of(lineupBand, STUB);
        List<String> editedLineup = lineupBand.isBlank() ? List.of() : List.of(lineupBand);

        Gig gig = new Gig("TEST selenium client (smazat)",
                new GigSchedule(ZonedDateTime.of(2026, 11, 12, 20, 0, 0, 0, PRAGUE),
                        ZonedDateTime.of(2026, 11, 13, 1, 0, 0, 0, PRAGUE)),
                new Location("Lucerna Music Bar", "Praha", Country.CZECHIA), fullLineup,
                Admission.paid("200 Kč"), "created by the live test", "https://www.facebook.com/events/1",
                null, "https://bandzone.cz/img/default/band-single-small.png", false);
        Gig edited = new Gig("TEST selenium client edited (smazat)",
                GigSchedule.startingAt(ZonedDateTime.of(2026, 11, 14, 21, 30, 0, 0, PRAGUE)),
                // one of three towns called Hranice: the district picks Bandzone's right one
                new Location("Garáž u Nováků", "Hranice", Country.CZECHIA, new Address(null, "753 01",
                        "okres Přerov", "Olomoucký kraj", 49.548, 17.735)), editedLineup,
                Admission.voluntary(), "edited by the live test", null, null, null, false);

        assertThatCode(() -> {
            try (BandzoneSession session = client.openSession(BandId.FIRST)) {
                String id = session.createGig(gig);
                assertThat(id).matches("\\d+");
                try {
                    session.updateGig(id, gig);    // what publishing does after the wizard
                    session.updateGig(id, edited); // each throws if Bandzone did not store it
                    session.cancelGig(id);         // throws unless the band page lists it cancelled
                } finally {
                    if (keep) {
                        System.out.println("BZ_KEEP: kept concert " + id);
                    } else {
                        assertThat(session.deleteGig(id)).as("deleted").isTrue();
                        assertThat(session.deleteGig(id)).as("already gone").isFalse();
                    }
                }
            }
        }).doesNotThrowAnyException();
    }
}
