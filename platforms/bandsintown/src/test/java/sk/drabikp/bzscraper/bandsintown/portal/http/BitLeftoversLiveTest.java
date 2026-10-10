package sk.drabikp.bzscraper.bandsintown.portal.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import sk.drabikp.bzscraper.bandsintown.portal.BitSession;
import sk.drabikp.bzscraper.importing.domain.ImportedGig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reads the band's real Bandsintown events and names anything a test left behind — read-only, so it can
 * be run whenever. A live test says it removed what it made; this is how that claim is checked rather
 * than taken on trust (two events once outlived such a claim).
 */
@EnabledIfEnvironmentVariable(named = "BIT_LIVE", matches = "true")
class BitLeftoversLiveTest {

    @Test
    void no_test_event_is_left_on_the_band() throws Exception {
        String baseUrl = System.getenv().getOrDefault("BIT_BASE_URL", "https://artists.bandsintown.com");
        BitConnection connection = BitConnection.read(
                Files.readString(Path.of(System.getenv("BIT_CONNECTION_FILE"))));
        BitSession session = new HttpBitSession(new BitWeb(baseUrl, new BitSignIn(baseUrl, connection,
                Clock.systemUTC())), "", Clock.systemDefaultZone());

        List<ImportedGig> all = session.listEvents();
        List<String> leftovers = all.stream()
                .filter(event -> event.gig().title().toUpperCase().contains("BZSCRAPER")
                        || event.gig().title().toUpperCase().contains("TEST"))
                .map(event -> event.externalRef() + " " + event.gig().schedule().start().toLocalDate()
                        + " " + event.gig().title())
                .toList();
        session.close();

        System.out.println("BIT holds " + all.size() + " event(s); test-looking: " + leftovers);
        assertThat(leftovers).as("events a test left behind").isEmpty();
    }
}
