package sk.drabikp.bzscraper.browser;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;

import java.time.Duration;
import java.util.concurrent.Semaphore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WarmBrowserTest {

    private final Semaphore oneBrowser = new Semaphore(1);

    @Test
    void the_next_session_takes_over_the_parked_browser() {
        WarmBrowser warm = new WarmBrowser(oneBrowser, Duration.ofMinutes(1), "test");
        WebDriver driver = mock(WebDriver.class);

        warm.park(driver, "a");

        assertThat(warm.take("a")).isSameAs(driver);
        assertThat(warm.take("a")).as("taken once").isNull();
        verify(driver, never()).quit();
    }

    @Test
    void another_accounts_browser_is_closed_not_handed_out() {
        WarmBrowser warm = new WarmBrowser(oneBrowser, Duration.ofMinutes(1), "test");
        WebDriver driver = mock(WebDriver.class);

        warm.park(driver, "a");

        assertThat(warm.take("b")).as("its profile is another account's").isNull();
        verify(driver).quit();
        assertThat(warm.take("a")).as("gone for its own account too").isNull();
    }

    @Test
    void a_browser_that_died_meanwhile_is_closed_not_handed_out() {
        WarmBrowser warm = new WarmBrowser(oneBrowser, Duration.ofMinutes(1), "test");
        WebDriver driver = mock(WebDriver.class);
        when(driver.getWindowHandle()).thenThrow(new WebDriverException("gone"));

        warm.park(driver, "a");

        assertThat(warm.take("a")).isNull();
        verify(driver).quit();
    }

    @Test
    void an_idle_browser_is_closed_unless_someone_holds_the_browser() throws Exception {
        WarmBrowser warm = new WarmBrowser(oneBrowser, Duration.ofMillis(50), "test");
        WebDriver idle = mock(WebDriver.class);
        warm.park(idle, "a");
        verify(idle, timeout(2000)).quit();

        WebDriver busy = mock(WebDriver.class);
        oneBrowser.acquire();                       // a session is using the platform's browser
        warm.park(busy, "a");
        Thread.sleep(200);
        verify(busy, never()).quit();
        oneBrowser.release();
        warm.close();
        verify(busy).quit();
    }

    @Test
    void once_closed_a_browser_handed_back_is_closed_at_once() {
        WarmBrowser warm = new WarmBrowser(oneBrowser, Duration.ofMinutes(1), "test");
        WebDriver driver = mock(WebDriver.class);

        warm.close();
        warm.park(driver, "a");                          // no RejectedExecutionException from the stopped timer

        verify(driver).quit();
        assertThat(warm.take("a")).isNull();
    }
}
