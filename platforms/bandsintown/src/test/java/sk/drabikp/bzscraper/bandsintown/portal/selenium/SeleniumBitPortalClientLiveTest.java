package sk.drabikp.bzscraper.bandsintown.portal.selenium;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import sk.drabikp.bzscraper.accounts.application.AccountsFakes;
import sk.drabikp.bzscraper.bandsintown.BandsintownPlatform;
import sk.drabikp.bzscraper.bandsintown.BandsintownProperties;
import sk.drabikp.bzscraper.bandsintown.RemovalReason;
import sk.drabikp.bzscraper.bandsintown.portal.BitSession.Created;
import sk.drabikp.bzscraper.bandsintown.portal.BitSession;
import sk.drabikp.bzscraper.browser.BrowserPool;
import sk.drabikp.bzscraper.browser.BrowserProperties;
import sk.drabikp.bzscraper.browser.SeleniumOptions;
import sk.drabikp.bzscraper.gig.domain.Admission;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.Country;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.GigSchedule;
import sk.drabikp.bzscraper.gig.domain.Location;
import sk.drabikp.bzscraper.importing.domain.ImportedGig;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Live integration test — drives the real Bandsintown artist portal: CREATES a made-up
 * event (published WITHOUT notifying followers), EDITS it, then DELETES it as cancelled
 * (cleanup runs even when a step fails), and checks that deleting it again is a no-op.
 * Skipped unless {@code BIT_LIVE=true}. Reads creds from env so nothing is committed:
 *   BIT_LOGIN, BIT_PASSWORD, BIT_TOTP (authenticator secret)  (required)
 *   BIT_CLEANUP_ID  only delete this event id (e.g. one a failed run left behind)
 *   BIT_INSPECT_IDS only print how the portal lists these event ids (comma-separated; published
 *                   events, as the import reads them) — read-only
 *   BIT_PAST_DELETE=true  instead: create a made-up PAST event (silently), remove it through its
 *                   form (what the event list can't do) and check that removing it again is a no-op
 *   BIT_HEADLESS (default true), BIT_CHROMIUM (default /usr/bin/chromium),
 *   BIT_CHROMEDRIVER (default /usr/bin/chromedriver), BIT_PROFILE (browser profile dir)
 *
 * The event is public for about a minute on the account's artist page.
 */
@EnabledIfEnvironmentVariable(named = "BIT_LIVE", matches = "true")
class SeleniumBitPortalClientLiveTest {

    private static final ZoneId BRATISLAVA = ZoneId.of("Europe/Bratislava");

    private static String env(String name, String fallback) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? fallback : v;
    }

    /** How the portal lists these events (upcoming and past, as the import reads them): "start at venue, city (lat, lon)". */
    private static Map<String, String> listed(BitSession session, Collection<String> ids) throws Exception {
        Map<String, String> found = new LinkedHashMap<>();
        for (ImportedGig event : session.listEvents()) {
            if (ids.contains(event.externalRef())) {
                found.put(event.externalRef(), event.gig().schedule().start() + " at " + event.gig().location().venue()
                        + ", " + event.gig().location().city() + " (" + event.latitude() + ", " + event.longitude() + ")");
            }
        }
        return found;
    }

    @Test
    void creates_publishes_silently_edits_and_deletes_an_event() {
        SeleniumBitPortalClient client = new SeleniumBitPortalClient(
                new BandsintownProperties(null, null, null, null, null, null, false, new SeleniumOptions(true,
                        Boolean.parseBoolean(env("BIT_HEADLESS", "true")), env("BIT_CHROMIUM", "/usr/bin/chromium"),
                        env("BIT_CHROMEDRIVER", "/usr/bin/chromedriver"), env("BIT_PROFILE", ""), null),
                        new BandsintownProperties.Pacing(600L, 1500L)),
                new BrowserProperties(env("BIT_PASSWORD_STORE", "auto"), null, null, null), Clock.systemDefaultZone(),
                new AccountsFakes.Accounts().with(BandId.FIRST, BandsintownPlatform.PLATFORM,
                        Map.of("login", env("BIT_LOGIN", ""), "artistName", env("BIT_ARTIST_NAME", "")),
                        Map.of("password", env("BIT_PASSWORD", ""), "totpSecret", env("BIT_TOTP", ""))),
                new BrowserPool(1));

        Gig gig = new Gig("BZSCRAPER TEST - please ignore",
                GigSchedule.startingAt(ZonedDateTime.of(2027, 10, 7, 20, 0, 0, 0, BRATISLAVA)),
                new Location("BZSCRAPER TEST VENUE", "Bratislava", Country.SLOVAKIA), List.of(),
                Admission.free(), "Automated test event - will be deleted.", null, null, null, false);
        Gig edited = new Gig("BZSCRAPER TEST - please ignore",
                GigSchedule.startingAt(ZonedDateTime.of(2027, 10, 7, 21, 30, 0, 0, BRATISLAVA)),
                new Location("BZSCRAPER TEST VENUE", "Bratislava", Country.SLOVAKIA), List.of(),
                Admission.free(), "Automated test event - edited, will be deleted.", null, null, null, false);

        String inspectIds = env("BIT_INSPECT_IDS", "");
        if (!inspectIds.isBlank()) {
            assertThatCode(() -> {
                try (BitSession session = client.openSession(BandId.FIRST)) {
                    List<String> ids = List.of(inspectIds.split(","));
                    Map<String, String> found = listed(session, ids);
                    ids.forEach(id -> System.out.println("BIT inspect: " + id + " -> "
                            + found.getOrDefault(id, "not listed (upcoming or past)")));
                }
            }).doesNotThrowAnyException();
            return;
        }

        if (Boolean.parseBoolean(env("BIT_PAST_DELETE", "false"))) {
            Gig past = new Gig("BZSCRAPER TEST - please ignore",
                    GigSchedule.startingAt(ZonedDateTime.of(2026, 10, 1, 20, 0, 0, 0, BRATISLAVA)),
                    new Location("BZSCRAPER TEST VENUE", "Bratislava", Country.SLOVAKIA), List.of(),
                    Admission.free(), "Automated test event - will be deleted.", null, null, null, false);
            assertThatCode(() -> {
                try (BitSession session = client.openSession(BandId.FIRST)) {
                    List<Created> created = session.createEvents(List.of(past), false);
                    assertThat(created).singleElement().satisfies(c ->
                            assertThat(c.isPublished()).as("created: " + c.error()).isTrue());
                    String id = created.getFirst().eventId();
                    try {
                        session.deleteEventInForm(id, RemovalReason.OTHER);
                    } finally {
                        session.deleteEventInForm(id, RemovalReason.OTHER);    // already gone: no-op (or the cleanup)
                    }
                    Map<String, String> left = listed(session, List.of(id));
                    assertThat(left).as("still listed").isEmpty();
                    System.out.println("BIT live test: created past event " + id + " and removed it through its form");
                }
            }).doesNotThrowAnyException();
            return;
        }

        String cleanupId = env("BIT_CLEANUP_ID", "");
        if (!cleanupId.isBlank()) {
            assertThatCode(() -> {
                try (BitSession session = client.openSession(BandId.FIRST)) {
                    session.deleteEvent(cleanupId, RemovalReason.OTHER);
                }
            }).doesNotThrowAnyException();
            return;
        }

        assertThatCode(() -> {
            try (BitSession session = client.openSession(BandId.FIRST)) {
                List<Created> created = session.createEvents(List.of(gig), false);
                assertThat(created).singleElement().satisfies(c ->
                        assertThat(c.isPublished()).as("created: " + c.error()).isTrue());
                String id = created.getFirst().eventId();
                try {
                    assertThat(session.updateEvents(List.of(new BitSession.EventEdit(id, edited))))
                            .singleElement().satisfies(e -> assertThat(e.isUpdated()).as(e.error()).isTrue());
                } finally {
                    session.deleteEvent(id, RemovalReason.CANCELED);
                    session.deleteEvent(id, RemovalReason.CANCELED);      // already gone: no-op
                    System.out.println("BIT live test: created, edited and deleted event " + id);
                }
            }
        }).doesNotThrowAnyException();
    }
}
