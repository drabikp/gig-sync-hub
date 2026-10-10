package sk.drabikp.bzscraper.bandsintown.portal.selenium;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import sk.drabikp.bzscraper.bandsintown.BitPortalException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The portal's own API replies, read in the page: {@link #CAPTURE} wraps {@code window.fetch}
 * and keeps each reply in {@code window.__bz} (a hand-made API call would lack the portal's
 * request signature, so the app never calls the API itself — it reads what the page got).
 * Take {@link #count()} before an action, then {@link #await} the reply to it.
 */
final class PortalReplies {

    private static final Duration WAIT = Duration.ofSeconds(90);

    /**
     * Records the portal's event and API replies in {@code window.__bz}. Registered to run
     * before any page script (see {@link SeleniumBitPortalClient}), so even the lists a page
     * fetches while loading are seen; also run after load as a fallback.
     */
    public static final String CAPTURE = """
            (() => {
            if (window.__bzHooked) return;
            window.__bzHooked = true;
            window.__bz = [];
            const original = window.fetch;
            window.fetch = function (u, o) {
              const url = String(u && u.url || u);
              const method = String((o && o.method) || (u && u.method) || 'GET').toUpperCase();
              const reply = original.apply(this, arguments);
              if (/\\/managed-actors\\/\\d+\\/events|\\/api\\//.test(url)) {
                reply.then(r => r.clone().text().then(t => {
                  let json = null;
                  try { json = JSON.parse(t); } catch (e) {}
                  window.__bz.push({method, url, status: r.status, json, text: t.slice(0, 2000),
                                    next: r.headers.get('x-next-page')});
                })).catch(e => window.__bz.push({method, url, status: -1, json: null, text: String(e)}));
              }
              return reply;
            };
            })();""";

    private final WebDriver driver;

    PortalReplies(WebDriver driver) {
        this.driver = driver;
    }

    /** Hooks the page's {@code fetch} if the page loaded without it. */
    void install() {
        js(CAPTURE);
    }

    /** How many replies the page has seen so far — await only the ones after it. */
    int count() {
        Object count = js("return (window.__bz || []).length;");
        return count instanceof Number n ? n.intValue() : 0;
    }

    PortalReply await(int from, Predicate<PortalReply> match, String what) throws BitPortalException {
        return await(from, match, what, WAIT);
    }

    /** The first reply after {@code from} that matches; a timeout is a temporary failure. */
    PortalReply await(int from, Predicate<PortalReply> match, String what, Duration timeout)
            throws BitPortalException {
        try {
            return new WebDriverWait(driver, timeout).until(d ->
                    after(from).stream().filter(match).findFirst().orElse(null));
        } catch (TimeoutException e) {
            throw BitPortalException.temporary("Bandsintown did not answer " + what + " in time.");
        }
    }

    /**
     * The last matching reply after {@code from}, once no new one has come for {@code quiet} —
     * for a search that answers while the user types (the reply to the whole query comes last).
     */
    PortalReply awaitLast(int from, Predicate<PortalReply> match, String what, Duration quiet)
            throws BitPortalException {
        int[] seen = {0};
        long[] since = {0};
        try {
            return new WebDriverWait(driver, WAIT).pollingEvery(Duration.ofMillis(200)).until(d -> {
                List<PortalReply> matching = after(from).stream().filter(match).toList();
                long now = System.nanoTime();
                if (matching.size() != seen[0]) {
                    seen[0] = matching.size();
                    since[0] = now;
                    return null;
                }
                return !matching.isEmpty() && now - since[0] >= quiet.toNanos() ? matching.getLast() : null;
            });
        } catch (TimeoutException e) {
            throw BitPortalException.temporary("Bandsintown did not answer " + what + " in time.");
        }
    }

    @SuppressWarnings("unchecked")
    private List<PortalReply> after(int from) {
        Object replies = js("return (window.__bz || []).slice(arguments[0]);", from);
        if (!(replies instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(raw -> raw instanceof Map<?, ?>)
                .map(raw -> PortalReply.of((Map<String, ?>) raw)).toList();
    }

    private Object js(String script, Object... args) {
        return ((JavascriptExecutor) driver).executeScript(script, args);
    }
}
