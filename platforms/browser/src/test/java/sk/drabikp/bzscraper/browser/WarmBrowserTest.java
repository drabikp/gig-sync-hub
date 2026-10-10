package sk.drabikp.bzscraper.browser;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WarmBrowserTest {

    private final List<WebDriver> closed = new ArrayList<>();

    private WarmBrowser warm(Duration idle) {
        return new WarmBrowser(idle, "test", driver -> {
            synchronized (closed) {
                closed.add(driver);
            }
            driver.quit();
        });
    }

    @Test
    void the_accounts_next_session_takes_over_its_parked_browser() {
        WarmBrowser warm = warm(Duration.ofMinutes(1));
        WebDriver driver = mock(WebDriver.class);

        warm.park(driver, "a");

        assertThat(warm.take("a")).isSameAs(driver);
        assertThat(warm.take("a")).as("taken once").isNull();
        verify(driver, never()).quit();
    }

    @Test
    void each_account_has_its_own_and_another_accounts_isnt_handed_out() {
        WarmBrowser warm = warm(Duration.ofMinutes(1));
        WebDriver a = mock(WebDriver.class);
        WebDriver b = mock(WebDriver.class);

        warm.park(a, "a");
        warm.park(b, "b");

        assertThat(warm.take("c")).isNull();
        assertThat(warm.take("b")).isSameAs(b);
        assertThat(warm.size()).isEqualTo(1);
        assertThat(closed).isEmpty();
    }

    @Test
    void a_browser_that_died_meanwhile_is_closed_not_handed_out() {
        WarmBrowser warm = warm(Duration.ofMinutes(1));
        WebDriver driver = mock(WebDriver.class);
        when(driver.getWindowHandle()).thenThrow(new WebDriverException("gone"));

        warm.park(driver, "a");

        assertThat(warm.take("a")).isNull();
        assertThat(closed).containsExactly(driver);
    }

    @Test
    void an_idle_browser_is_closed_after_a_while_and_the_idlest_first_when_room_is_needed() throws Exception {
        WarmBrowser warm = warm(Duration.ofMillis(50));
        WebDriver idle = mock(WebDriver.class);
        warm.park(idle, "a");
        verify(idle, timeout(2000)).quit();

        WarmBrowser kept = warm(Duration.ofMinutes(1));
        WebDriver older = mock(WebDriver.class);
        WebDriver newer = mock(WebDriver.class);
        kept.park(older, "a");
        Thread.sleep(5);
        kept.park(newer, "b");

        assertThat(kept.closeOldest()).isTrue();
        assertThat(closed).contains(older).doesNotContain(newer);
        assertThat(kept.take("b")).isSameAs(newer);
        assertThat(kept.closeOldest()).as("nothing parked").isFalse();
    }

    @Test
    void once_closed_a_browser_handed_back_is_closed_at_once() {
        WarmBrowser warm = warm(Duration.ofMinutes(1));
        WebDriver parked = mock(WebDriver.class);
        warm.park(parked, "a");
        WebDriver late = mock(WebDriver.class);

        warm.close();
        warm.park(late, "b");                       // no RejectedExecutionException from the stopped timer

        assertThat(closed).containsExactly(parked, late);
        assertThat(warm.take("b")).isNull();
    }
}
