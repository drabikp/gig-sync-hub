package sk.drabikp.bzscraper.bandzone.portal.selenium;

import jakarta.annotation.PreDestroy;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.accounts.application.port.in.PlatformAccounts;
import sk.drabikp.bzscraper.bandzone.BandzoneAccount;
import sk.drabikp.bzscraper.bandzone.BandzonePortalException;
import sk.drabikp.bzscraper.bandzone.portal.PosterDownloader;
import sk.drabikp.bzscraper.bandzone.BandzoneProperties;
import sk.drabikp.bzscraper.bandzone.portal.BandzonePortalClient;
import sk.drabikp.bzscraper.bandzone.portal.BandzoneSession;
import sk.drabikp.bzscraper.browser.BrowserPool;
import sk.drabikp.bzscraper.browser.BrowserProperties;
import sk.drabikp.bzscraper.browser.OnPlatformClient;
import sk.drabikp.bzscraper.browser.PlatformClient;
import sk.drabikp.bzscraper.browser.PlatformBrowser;
import sk.drabikp.bzscraper.gig.domain.BandId;

import java.time.Duration;

/**
 * Real Bandzone publisher: opens ONE headless-browser session per batch (single
 * login) and creates each gig through the band-admin 2-step wizard, edits it through
 * the concert's update form (details, venue, poster) and performing-bands tab (lineup),
 * and cancels/deletes it through the update delete tab. The fallback to the HTTP client, active only
 * with {@code bzscraper.bandzone.client=selenium} (or the older {@code selenium.enabled=true}). The
 * login is the band's own ({@link BandzoneAccount}).
 *
 * The browser ({@link PlatformBrowser}: one at a time, kept warm, a private profile per band's
 * account) keeps a saved login: the password is used only when Bandzone ended that login
 * ({@link BandzoneLoginPage}). This client only takes the browser, logs in through that page
 * and makes the session.
 *
 * Field values are set via JavaScript (with input/change events) rather than
 * sendKeys, because Bandzone's date/city inputs are JS-wrapped and not directly
 * interactable — the approach that validated the flow manually.
 */
@Component
@OnPlatformClient(platform = "bandzone", value = PlatformClient.SELENIUM)
class SeleniumBandzonePortalClient implements BandzonePortalClient {

    /** What a failure in Bandzone's browser means, as a {@link BandzonePortalException}. */
    static final PlatformBrowser.Failures<BandzonePortalException> FAILURES = new PlatformBrowser.Failures<>(
            "Bandzone", BandzonePortalException::temporary, BandzonePortalException::busy);
    private static final Duration WAIT = Duration.ofSeconds(20);

    private final String baseUrl;
    private final PlatformAccounts accounts;
    private final PlatformBrowser browser;
    private final PosterDownloader posters = new PosterDownloader();

    public SeleniumBandzonePortalClient(BandzoneProperties properties, BrowserProperties browserProperties,
                                        PlatformAccounts accounts, BrowserPool pool) {
        this.baseUrl = properties.baseUrl();
        this.accounts = accounts;
        this.browser = new PlatformBrowser(properties.selenium().browser("bandzone", browserProperties, "1280,1024"),
                new PlatformBrowser.Customizer() {
                }, pool);
    }

    @Override
    public BandzoneSession openSession(BandId band, Duration waitForBrowser) throws BandzonePortalException {
        BandzoneAccount account = BandzoneAccount.of(accounts, band);
        return browser.open(account.browserKey(), waitForBrowser, FAILURES, lease -> {
            BandzoneBrowser bandzone = new BandzoneBrowser(lease.driver(), new WebDriverWait(lease.driver(), WAIT),
                    baseUrl, account.bandSlug());
            new BandzoneLoginPage(bandzone, account.login(), account.password()).logIn();
            return new SeleniumBandzoneSession(lease, bandzone, posters);
        });
    }

    @PreDestroy
    void closeBrowser() {
        browser.close();
    }
}
