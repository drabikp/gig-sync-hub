package sk.drabikp.gigsynchub.bandsintown.portal.http;

import org.springframework.stereotype.Component;
import sk.drabikp.gigsynchub.accounts.application.port.in.PlatformAccounts;
import sk.drabikp.gigsynchub.bandsintown.BandsintownAccount;
import io.micrometer.observation.ObservationRegistry;
import sk.drabikp.gigsynchub.portal.PlatformEgress;
import sk.drabikp.gigsynchub.bandsintown.BandsintownProperties;
import sk.drabikp.gigsynchub.bandsintown.BitPortalException;
import sk.drabikp.gigsynchub.bandsintown.portal.BitPortalClient;
import sk.drabikp.gigsynchub.bandsintown.portal.BitSession;
import sk.drabikp.gigsynchub.bandsintown.portal.HumanPacer;
import sk.drabikp.gigsynchub.portal.OnPlatformClient;
import sk.drabikp.gigsynchub.portal.PlatformClient;
import sk.drabikp.gigsynchub.gig.domain.BandId;

import java.time.Clock;
import java.time.Duration;

/**
 * Bandsintown without a browser ({@code gigsynchub.bandsintown.client=http}): the artist portal has no write
 * API for artists, so this client talks to the API its own pages talk to, with the session a band's one
 * browser sign-in makes possible ({@link BitSignIn}). Nothing is waited for — a session is a few requests,
 * not a scarce browser — but the requests are paced like a person's
 * ({@code gigsynchub.bandsintown.pacing.min-ms / .max-ms}), which is the point of not bursting.
 *
 * <p>Each band's session is worked out again per batch of work and kept only as long as it is fresh; the
 * refresh token is read from the account each time, so reconnecting takes effect at once.
 */
@Component
@OnPlatformClient(platform = "bandsintown", value = PlatformClient.HTTP)
class HttpBitPortalClient implements BitPortalClient {

    private final ObservationRegistry observations;
    private final PlatformEgress egress;
    private final String baseUrl;
    private final PlatformAccounts accounts;
    private final Clock clock;
    private final HumanPacer pacer;

    HttpBitPortalClient(BandsintownProperties properties, PlatformAccounts accounts, Clock clock,
                        ObservationRegistry observations) {
        this.baseUrl = properties.baseUrl();
        this.accounts = accounts;
        this.clock = clock;
        this.observations = observations;
        this.egress = properties.egress();
        this.pacer = new HumanPacer(properties.pacing().minMs(), properties.pacing().maxMs());
    }

    @Override
    public BitSession openSession(BandId band, Duration wait) throws BitPortalException {
        BandsintownAccount account = BandsintownAccount.of(accounts, band);
        BitConnection connection = new BitConnection(account.refreshToken(), account.managerId(),
                account.actorId(), account.fingerprint());
        BitWeb web = new BitWeb(baseUrl, new BitSignIn(baseUrl, connection, clock, egress), pacer,
                observations, egress);
        return new HttpBitSession(web, account.artistName(), clock, pacer);
    }
}
