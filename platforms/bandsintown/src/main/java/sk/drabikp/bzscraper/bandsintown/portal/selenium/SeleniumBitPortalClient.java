package sk.drabikp.bzscraper.bandsintown.portal.selenium;

import jakarta.annotation.PreDestroy;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.accounts.application.port.in.PlatformAccounts;
import sk.drabikp.bzscraper.bandsintown.BandsintownAccount;
import sk.drabikp.bzscraper.bandsintown.BandsintownProperties;
import sk.drabikp.bzscraper.bandsintown.BitPortalException;
import sk.drabikp.bzscraper.bandsintown.portal.BitPortalClient;
import sk.drabikp.bzscraper.bandsintown.portal.BitSession;
import sk.drabikp.bzscraper.browser.BrowserPool;
import sk.drabikp.bzscraper.browser.BrowserProperties;
import sk.drabikp.bzscraper.browser.PlatformBrowser;
import sk.drabikp.bzscraper.gig.domain.BandId;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;

/**
 * Real Bandsintown client: drives the artist portal (artists.bandsintown.com) in a
 * headless Chromium, because Bandsintown offers artists no write API and its internal
 * API signs every request. Active only when
 * {@code bzscraper.bandsintown.selenium.enabled=true}; the login is the band's own
 * ({@link BandsintownAccount}). It only takes the browser, logs in through
 * {@link BitLoginPage} and makes the session.
 *
 * <p>To look like a person rather than a bot: one browser at a time and a private
 * profile per band's account ({@link PlatformBrowser} — a still-valid login is reused instead of
 * logging in with a fresh authenticator code every time), no automation flags or
 * "HeadlessChrome" user agent, and all clicks/typing paced by {@link HumanPacer}.
 */
@Component
@ConditionalOnProperty(name = "bzscraper.bandsintown.selenium.enabled", havingValue = "true")
class SeleniumBitPortalClient implements BitPortalClient {

    /** What a failure in Bandsintown's browser means, as a {@link BitPortalException}. */
    static final PlatformBrowser.Failures<BitPortalException> FAILURES = new PlatformBrowser.Failures<>(
            "Bandsintown", BitPortalException::temporary, BitPortalException::busy);
    private static final Duration WAIT = Duration.ofSeconds(25);

    private final String baseUrl;
    private final PlatformAccounts accounts;
    private final Clock clock;
    private final HumanPacer pacer;
    private final PlatformBrowser browser;

    public SeleniumBitPortalClient(BandsintownProperties properties, BrowserProperties browserProperties,
                                   Clock clock, PlatformAccounts accounts, BrowserPool pool) {
        this.baseUrl = properties.baseUrl();
        this.accounts = accounts;
        this.clock = clock;
        this.pacer = new HumanPacer(properties.pacing().minMs(), properties.pacing().maxMs());
        this.browser = new PlatformBrowser(properties.selenium().browser("bandsintown", browserProperties,
                "1366,900"), new PlatformBrowser.Customizer() {
            @Override
            public void options(ChromeOptions options) {
                PlatformBrowser.looksLikeAPerson(options, "en-US");
            }

            @Override
            public void started(ChromeDriver driver) {
                PlatformBrowser.normalUserAgent(driver, "en-US");
                // record the portal's replies from the first request of every page (see PortalReplies)
                driver.executeCdpCommand("Page.addScriptToEvaluateOnNewDocument",
                        Map.of("source", PortalReplies.CAPTURE));
            }
        }, pool);
    }

    @Override
    public BitSession openSession(BandId band, Duration waitForBrowser) throws BitPortalException {
        BandsintownAccount account = BandsintownAccount.of(accounts, band);
        return browser.open(account.browserKey(), waitForBrowser, FAILURES, lease -> {
            BitPortal portal = new BitPortal(lease.driver(), new WebDriverWait(lease.driver(), WAIT), pacer, baseUrl);
            String artistId = new BitLoginPage(portal, account, clock).logIn();
            return new SeleniumBitSession(lease, portal.forArtist(artistId), account.artistName(), clock);
        });
    }

    @PreDestroy
    void closeBrowser() {
        browser.close();
    }
}
