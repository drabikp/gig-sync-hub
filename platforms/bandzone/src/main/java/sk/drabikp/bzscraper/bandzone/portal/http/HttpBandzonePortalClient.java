package sk.drabikp.bzscraper.bandzone.portal.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.accounts.application.port.in.PlatformAccounts;
import sk.drabikp.bzscraper.bandzone.BandzoneAccount;
import sk.drabikp.bzscraper.bandzone.BandzonePortalException;
import sk.drabikp.bzscraper.bandzone.BandzoneProperties;
import sk.drabikp.bzscraper.bandzone.portal.BandzonePortalClient;
import sk.drabikp.bzscraper.bandzone.portal.BandzoneSession;
import sk.drabikp.bzscraper.bandzone.portal.PosterDownloader;
import sk.drabikp.bzscraper.browser.OnPlatformClient;
import sk.drabikp.bzscraper.browser.PlatformClient;
import sk.drabikp.bzscraper.gig.domain.BandId;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bandzone without a browser ({@code bzscraper.bandzone.client=http}): the band admin's pages are plain
 * server-rendered forms whose widgets send the form back through AJAX, so this client sends exactly what
 * the pages send ({@link HttpBandzoneSession}). Each band's account keeps its cookies in memory, so its
 * login is reused until Bandzone ends it; the password is sent only then. Nothing to wait for: a
 * session is a few requests, never a scarce browser.
 */
@Component
@OnPlatformClient(platform = "bandzone", value = PlatformClient.HTTP)
class HttpBandzonePortalClient implements BandzonePortalClient {

    private static final Logger logger = LoggerFactory.getLogger(HttpBandzonePortalClient.class);

    private final String baseUrl;
    private final PlatformAccounts accounts;
    private final PosterDownloader posters;
    private final Map<String, BandzoneWeb> logins = new ConcurrentHashMap<>();

    HttpBandzonePortalClient(BandzoneProperties properties, PlatformAccounts accounts) {
        this(properties.baseUrl(), accounts, new PosterDownloader());
    }

    HttpBandzonePortalClient(String baseUrl, PlatformAccounts accounts, PosterDownloader posters) {
        this.baseUrl = baseUrl;
        this.accounts = accounts;
        this.posters = posters;
    }

    @Override
    public BandzoneSession openSession(BandId band, Duration wait) throws BandzonePortalException {
        BandzoneAccount account = BandzoneAccount.of(accounts, band);
        BandzoneWeb web = logins.computeIfAbsent(account.browserKey(), key -> new BandzoneWeb(baseUrl));
        if (web.loggedIn()) {
            logger.info("Bandzone: reusing the login");
        } else {
            web.logIn(account.login(), account.password());
        }
        return new HttpBandzoneSession(web, account.bandSlug(), posters);
    }
}
