package sk.drabikp.bzscraper.browser;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openqa.selenium.WebDriverException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformBrowserTest {

    @TempDir
    Path tmp;

    private final AtomicInteger started = new AtomicInteger();
    private PlatformBrowser browser;

    private PlatformBrowser browser() {
        TestChromium.assumeInstalled();
        browser = new PlatformBrowser(TestChromium.settings("test", tmp.resolve("profiles")),
                new PlatformBrowser.Customizer() {
                    @Override
                    public void started(org.openqa.selenium.chrome.ChromeDriver driver) {
                        started.incrementAndGet();
                    }
                });
        return browser;
    }

    @AfterEach
    void close() {
        if (browser != null) {
            browser.close();
        }
    }

    @Test
    void one_operation_at_a_time_the_next_finds_it_busy_or_waits() {
        PlatformBrowser browser = browser();
        PlatformBrowser.Lease first = browser.acquire("a", Duration.ZERO).orElseThrow();

        assertThat(browser.acquire("a", Duration.ZERO)).as("busy, not waited for").isEmpty();
        assertThat(browser.acquire("a", Duration.ofMillis(200))).as("busy for the whole wait").isEmpty();
        first.keepWarm();
        assertThat(browser.acquire("a", Duration.ZERO)).isPresent();
    }

    @Test
    void a_session_kept_warm_is_taken_over_and_a_discarded_one_starts_afresh() throws Exception {
        PlatformBrowser browser = browser();
        PlatformBrowser.Lease first = browser.acquire("a", Duration.ZERO).orElseThrow();
        assertThat(first.wasWarm()).isFalse();
        first.keepWarm();
        first.keepWarm();                               // ending twice is harmless

        PlatformBrowser.Lease second = browser.acquire("a", Duration.ZERO).orElseThrow();
        assertThat(second.wasWarm()).isTrue();
        assertThat(second.driver()).isSameAs(first.driver());
        second.discard();

        Optional<PlatformBrowser.Lease> third = browser.acquire("a", Duration.ZERO);
        assertThat(third).get().extracting(PlatformBrowser.Lease::wasWarm).isEqualTo(false);
        assertThat(started).hasValue(2);
        assertThat(Files.getPosixFilePermissions(BrowserProfile.dir(tmp.resolve("profiles").toString(), "test", "a")))
                .hasSize(3);                            // owner only
        third.get().keepWarm();
    }

    @Test
    void a_browser_is_one_accounts_another_account_gets_its_own() {
        PlatformBrowser browser = browser();
        PlatformBrowser.Lease first = browser.acquire("a", Duration.ZERO).orElseThrow();
        first.keepWarm();

        PlatformBrowser.Lease other = browser.acquire("b", Duration.ZERO).orElseThrow();
        assertThat(other.wasWarm()).as("a's warm browser isn't b's").isFalse();
        assertThat(other.driver()).isNotSameAs(first.driver());
        assertThat(BrowserProfile.dir(tmp.resolve("profiles").toString(), "test", "b")).isDirectory();
        other.keepWarm();
    }

    @Test
    void a_failure_in_the_browser_leaves_a_screenshot_and_becomes_the_platforms_failure() {
        PlatformBrowser browser = browser();
        PlatformBrowser.Lease lease = browser.acquire("a", Duration.ZERO).orElseThrow();
        Path shot = Path.of(System.getProperty("java.io.tmpdir"), "bzscraper-test-probe.png");

        assertThatThrownBy(() -> lease.guarded("probe", "Platform failed", FAILURES, () -> {
            throw new IllegalStateException("no such element");
        })).isInstanceOfSatisfying(Failure.class, e -> {
            assertThat(e.busy).isFalse();
            assertThat(e).hasMessage("Platform failed: no such element");
        });
        assertThat(shot).exists();
        lease.discard();
    }

    @Test
    void an_operation_interrupted_by_the_app_stopping_is_busy_not_failed_and_stays_interrupted() {
        PlatformBrowser browser = browser();
        PlatformBrowser.Lease lease = browser.acquire("a", Duration.ZERO).orElseThrow();
        try {
            assertThatThrownBy(() -> lease.guarded("probe", "Platform failed", FAILURES, () -> {
                Thread.currentThread().interrupt();          // as the pacing does when the worker is stopped
                throw new IllegalStateException("Interrupted while pacing");
            })).isInstanceOfSatisfying(Failure.class, e -> assertThat(e.busy).isTrue());
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
            lease.discard();
        }
    }

    @Test
    void a_session_handed_back_after_the_app_closed_the_browser_is_closed_and_frees_the_browser() {
        PlatformBrowser browser = browser();
        PlatformBrowser.Lease lease = browser.acquire("a", Duration.ZERO).orElseThrow();

        browser.close();                                     // app shutdown while a step still runs
        lease.keepWarm();                                    // the step ends: no exception

        assertThatThrownBy(() -> lease.driver().getWindowHandle()).as("closed, not parked")
                .isInstanceOf(WebDriverException.class);
        PlatformBrowser.Lease next = browser.acquire("a", Duration.ZERO).orElseThrow();
        assertThat(next.wasWarm()).isFalse();
        next.discard();
    }

    @Test
    void opening_a_session_logs_in_in_the_browser_or_says_it_is_busy_and_a_failed_login_discards_it() throws Exception {
        PlatformBrowser browser = browser();
        String session = browser.open("a", Duration.ZERO, FAILURES, lease -> {
            assertThatThrownBy(() -> browser.open("a", Duration.ZERO, FAILURES, other -> "second"))
                    .isInstanceOfSatisfying(Failure.class, e -> {
                        assertThat(e.busy).isTrue();
                        assertThat(e).hasMessage("Another Test operation is using the browser.");
                    });
            lease.keepWarm();
            return "first";
        });
        assertThat(session).isEqualTo("first");

        assertThatThrownBy(() -> browser.open("a", Duration.ZERO, FAILURES, lease -> {
            throw new IllegalStateException("no login form");
        })).isInstanceOfSatisfying(Failure.class, e -> {
            assertThat(e.busy).isFalse();
            assertThat(e).hasMessage("Test login failed: no login form");
        });
        PlatformBrowser.Lease next = browser.acquire("a", Duration.ZERO).orElseThrow();
        assertThat(next.wasWarm()).as("the failed login's browser was discarded").isFalse();
        next.discard();
    }

    /** A platform's exception, as the failures make it. */
    static final class Failure extends Exception {

        final boolean busy;

        Failure(String message, boolean busy) {
            super(message);
            this.busy = busy;
        }
    }

    private static final PlatformBrowser.Failures<Failure> FAILURES = new PlatformBrowser.Failures<>("Test",
            (message, cause) -> new Failure(message, false), message -> new Failure(message, true));
}
