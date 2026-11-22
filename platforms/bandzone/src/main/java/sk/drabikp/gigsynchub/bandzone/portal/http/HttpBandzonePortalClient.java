package sk.drabikp.gigsynchub.bandzone.portal.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import sk.drabikp.gigsynchub.accounts.application.port.in.PlatformAccounts;
import sk.drabikp.gigsynchub.bandzone.BandzoneAccount;
import sk.drabikp.gigsynchub.bandzone.BandzonePortalException;
import io.micrometer.observation.ObservationRegistry;
import sk.drabikp.gigsynchub.portal.PlatformEgress;
import sk.drabikp.gigsynchub.bandzone.BandzoneProperties;
import sk.drabikp.gigsynchub.bandzone.portal.BandzonePortalClient;
import sk.drabikp.gigsynchub.bandzone.portal.BandzoneSession;
import sk.drabikp.gigsynchub.bandzone.portal.PosterDownloader;
import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.portal.OnPlatformClient;
import sk.drabikp.gigsynchub.portal.PlatformClient;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bandzone without a browser ({@code gigsynchub.bandzone.client=http}): since its redesign (2026-11-22) the
 * site's pages are a Next.js app over its own JSON API, so this client makes exactly the calls the pages
 * make ({@link HttpBandzoneSession}). Each band's account keeps its cookies in memory, so its sign-in is
 * reused until Bandzone ends it. Normally there is no password to send: the band's account keeps the
 * {@code session} that connecting it gave (90 days), and when Bandzone has ended that — or it is one kept
 * from before the redesign, which the new site doesn't know — the band connects again: nothing is retried
 * with a password this server doesn't have. Nothing to wait for: a session is a few requests.
 */
@Component
@OnPlatformClient(platform = "bandzone", value = PlatformClient.HTTP)
class HttpBandzonePortalClient implements BandzonePortalClient {

    private static final Logger logger = LoggerFactory.getLogger(HttpBandzonePortalClient.class);

    private final ObservationRegistry observations;
    private final PlatformEgress egress;
    private final String baseUrl;
    private final String uploadUrl;
    private final Duration nonceAge;
    private final PlatformAccounts accounts;
    private final PosterDownloader posters;
    private final Map<String, BandzoneWeb> logins = new ConcurrentHashMap<>();

    /** The one the wiring uses — said so, because the other constructor makes the choice ambiguous. */
    @Autowired
    HttpBandzonePortalClient(BandzoneProperties properties, PlatformAccounts accounts,
                             ObservationRegistry observations) {
        this(properties.baseUrl(), BandzoneWeb.uploadUrlOf(properties.baseUrl()), accounts, new PosterDownloader(),
                observations, properties.egress(), BandzoneWeb.NONCE_AGE);
    }

    /** For a test against the real site, which says where Bandzone is and how posters are fetched. */
    HttpBandzonePortalClient(String baseUrl, PlatformAccounts accounts, PosterDownloader posters) {
        this(baseUrl, BandzoneWeb.uploadUrlOf(baseUrl), accounts, posters, ObservationRegistry.NOOP,
                PlatformEgress.DIRECT, BandzoneWeb.NONCE_AGE);
    }

    /** For a test against a stand-in, which also serves the uploads and needs no wait for the nonce. */
    HttpBandzonePortalClient(String baseUrl, String uploadUrl, PlatformAccounts accounts, PosterDownloader posters) {
        this(baseUrl, uploadUrl, accounts, posters, ObservationRegistry.NOOP, PlatformEgress.DIRECT, Duration.ZERO);
    }

    private HttpBandzonePortalClient(String baseUrl, String uploadUrl, PlatformAccounts accounts,
                                     PosterDownloader posters, ObservationRegistry observations,
                                     PlatformEgress egress, Duration nonceAge) {
        this.observations = observations;
        this.egress = egress;
        this.baseUrl = baseUrl;
        this.uploadUrl = uploadUrl;
        this.nonceAge = nonceAge;
        this.accounts = accounts;
        this.posters = posters;
    }

    @Override
    public BandzoneSession openSession(BandId band, Duration wait) throws BandzonePortalException {
        BandzoneAccount account = BandzoneAccount.of(accounts, band);
        BandzoneWeb web = logins.computeIfAbsent(account.browserKey(),
                key -> new BandzoneWeb(baseUrl, uploadUrl, observations, egress, nonceAge));
        if (web.loggedIn()) {
            logger.info("Bandzone: reusing the login");
        } else {
            if (!account.session().isBlank()) {
                web.useSession(account.session());
            }
            if (!web.loggedIn()) {
                if (account.password().isBlank()) {
                    throw BandzonePortalException.needsUser("Bandzone has ended this band's sign-in — connect "
                            + "Bandzone again in Settings.");
                }
                web.logIn(account.login(), account.password());
            }
        }
        return new HttpBandzoneSession(web, account.bandSlug(), posters);
    }
}
