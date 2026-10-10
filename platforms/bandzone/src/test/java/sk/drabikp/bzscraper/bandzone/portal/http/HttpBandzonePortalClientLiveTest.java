package sk.drabikp.bzscraper.bandzone.portal.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import sk.drabikp.bzscraper.accounts.application.AccountsFakes;
import sk.drabikp.bzscraper.bandzone.BandzonePlatform;
import sk.drabikp.bzscraper.bandzone.portal.BandzoneSession;
import sk.drabikp.bzscraper.bandzone.portal.PosterDownloader;
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
 * The HTTP client against the REAL Bandzone, the same cycle as the Selenium client's live test: creates a
 * gig (every field, lineup and poster), edits it (another of three towns called Hranice), cancels it,
 * deletes it and deletes it again (already gone). Skipped unless {@code BZ_LIVE=true}; reads
 * {@code BZ_LOGIN}, {@code BZ_PASSWORD}, {@code BZ_SLUG} (required), {@code BZ_LINEUP_BAND} (a band that
 * gets notified; default none), {@code BZ_KEEP=true} (keep the gig). Use a test band — the gig is briefly
 * public.
 */
@EnabledIfEnvironmentVariable(named = "BZ_LIVE", matches = "true")
class HttpBandzonePortalClientLiveTest {

    private static final ZoneId PRAGUE = ZoneId.of("Europe/Prague");
    private static final String STUB = "Bzscraper Testovaci Interpret";

    private static String env(String name, String fallback) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? fallback : v;
    }

    @Test
    void creates_edits_cancels_and_deletes_a_gig_with_every_field_over_http() {
        HttpBandzonePortalClient client = new HttpBandzonePortalClient(env("BZ_BASE_URL", "https://bandzone.cz"),
                new AccountsFakes.Accounts().with(BandId.FIRST, BandzonePlatform.PLATFORM,
                        Map.of("login", env("BZ_LOGIN", ""), "bandSlug", env("BZ_SLUG", "")),
                        Map.of("password", env("BZ_PASSWORD", ""))), new PosterDownloader());
        String lineupBand = env("BZ_LINEUP_BAND", "");
        boolean keep = Boolean.parseBoolean(env("BZ_KEEP", "false"));

        List<String> fullLineup = lineupBand.isBlank() ? List.of(STUB) : List.of(lineupBand, STUB);
        List<String> editedLineup = lineupBand.isBlank() ? List.of() : List.of(lineupBand);
        Gig gig = new Gig("TEST http client (smazat)",
                new GigSchedule(ZonedDateTime.of(2026, 11, 12, 20, 0, 0, 0, PRAGUE),
                        ZonedDateTime.of(2026, 11, 13, 1, 0, 0, 0, PRAGUE)),
                new Location("Lucerna Music Bar", "Praha", Country.CZECHIA), fullLineup,
                Admission.paid("200 Kč"), "created by the live test\nsecond line", "https://www.facebook.com/events/1",
                null, "https://bandzone.cz/img/default/band-single-small.png", false);
        Gig edited = new Gig("TEST http client edited (smazat)",
                GigSchedule.startingAt(ZonedDateTime.of(2026, 11, 14, 21, 30, 0, 0, PRAGUE)),
                new Location("Garáž u Nováků", "Hranice", Country.CZECHIA, new Address(null, "753 01",
                        "okres Přerov", "Olomoucký kraj", 49.548, 17.735)), editedLineup,
                Admission.voluntary(), "edited by the live test", null, null, null, false);

        assertThatCode(() -> {
            try (BandzoneSession session = client.openSession(BandId.FIRST)) {
                assertThat(session.bandName()).isNotBlank();
                String id = session.createGig(gig);
                assertThat(id).matches("\\d+");
                try {
                    session.updateGig(id, gig);
                    session.updateGig(id, edited);
                    session.cancelGig(id);
                } finally {
                    if (keep) {
                        System.out.println("BZ_KEEP: kept concert " + id);
                    } else {
                        assertThat(session.deleteGig(id)).as("deleted").isTrue();
                        assertThat(session.deleteGig(id)).as("already gone").isFalse();
                    }
                }
            }
            try (BandzoneSession again = client.openSession(BandId.FIRST)) {
                assertThat(again.bandName()).as("the login is reused").isNotBlank();
            }
        }).doesNotThrowAnyException();
    }
}
