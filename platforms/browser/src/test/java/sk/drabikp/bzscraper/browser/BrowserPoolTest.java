package sk.drabikp.bzscraper.browser;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** The server's limit on open browsers, shared by the platforms (multi-band plan, phase 3) — mock browsers. */
class BrowserPoolTest {

    private final List<WebDriver> started = new ArrayList<>();

    private PlatformBrowser platform(String name, BrowserPool pool, int sessions) {
        return new PlatformBrowser(new PlatformBrowser.Settings(name, "/tmp/unused", true, "", "", "basic", "800,600",
                sessions), new PlatformBrowser.Customizer() {
        }, pool, account -> {
            WebDriver driver = mock(WebDriver.class);
            started.add(driver);
            return driver;
        });
    }

    @Test
    void the_server_opens_at_most_max_browsers_and_closes_the_idlest_warm_one_for_a_new_account() {
        BrowserPool pool = new BrowserPool(2);
        PlatformBrowser bz = platform("bz", pool, 2);
        PlatformBrowser bit = platform("bit", pool, 2);

        PlatformBrowser.Lease bandA = bz.acquire("a", Duration.ZERO).orElseThrow();
        PlatformBrowser.Lease bandB = bit.acquire("b", Duration.ZERO).orElseThrow();
        assertThat(pool.open()).isEqualTo(2);
        assertThat(bz.acquire("c", Duration.ofMillis(100))).as("every browser in use: waits, then busy").isEmpty();

        bandA.keepWarm();                                   // a's is idle now, kept warm
        Optional<PlatformBrowser.Lease> bandC = bit.acquire("c", Duration.ZERO);

        assertThat(bandC).as("the idle warm one closed to make room").isPresent();
        verify(started.getFirst()).quit();
        assertThat(pool.open()).isEqualTo(2);
        bandB.discard();
        bandC.get().keepWarm();
        assertThat(pool.open()).as("one closed, one warm").isEqualTo(1);
    }

    @Test
    void an_account_has_one_session_and_a_platform_its_limit_of_them() {
        BrowserPool pool = new BrowserPool(5);
        PlatformBrowser bz = platform("bz", pool, 2);

        PlatformBrowser.Lease first = bz.acquire("a", Duration.ZERO).orElseThrow();
        assertThat(bz.acquire("a", Duration.ofMillis(50))).as("the same account: never two sessions").isEmpty();
        PlatformBrowser.Lease other = bz.acquire("b", Duration.ZERO).orElseThrow();
        assertThat(bz.acquire("c", Duration.ofMillis(50))).as("the platform's two sessions in use").isEmpty();
        assertThat(bz.sessionsInUse()).isEqualTo(2);

        first.keepWarm();
        PlatformBrowser.Lease again = bz.acquire("a", Duration.ZERO).orElseThrow();
        assertThat(again.wasWarm()).isTrue();
        assertThat(again.driver()).isSameAs(first.driver());
        again.keepWarm();
        other.keepWarm();
        assertThat(bz.sessionsInUse()).isZero();
        assertThat(pool.open()).isEqualTo(2);
    }

    @Test
    void a_session_waiting_for_room_gets_it_when_a_browser_closes() throws Exception {
        BrowserPool pool = new BrowserPool(1);
        PlatformBrowser bz = platform("bz", pool, 2);
        PlatformBrowser.Lease busy = bz.acquire("a", Duration.ZERO).orElseThrow();

        Thread closer = new Thread(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            busy.discard();
        });
        closer.start();
        Optional<PlatformBrowser.Lease> waited = bz.acquire("b", Duration.ofSeconds(5));
        closer.join();

        assertThat(waited).isPresent();
        assertThat(pool.open()).isEqualTo(1);
    }
}
