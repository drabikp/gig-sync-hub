package sk.drabikp.bzscraper.bandsintown.portal.http;

import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.accounts.application.port.out.PlatformSignIn;
import sk.drabikp.bzscraper.accounts.domain.PlatformAccount;
import sk.drabikp.bzscraper.bandsintown.BandsintownAccount;
import sk.drabikp.bzscraper.bandsintown.BandsintownPlatform;
import sk.drabikp.bzscraper.bandsintown.BandsintownProperties;
import sk.drabikp.bzscraper.bandsintown.BitPortalException;
import sk.drabikp.bzscraper.portal.OnPlatformClient;
import sk.drabikp.bzscraper.portal.PlatformClient;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What is kept when a band connects Bandsintown: it signed in at the portal in its OWN browser (the
 * portal's sign-in allows nothing else) and pasted the line its page printed; this reads that line, tries
 * it at once — renewing the sign-in and asking the portal for a session — and keeps only what works, so a
 * band never leaves Settings believing a paste that doesn't.
 *
 * <p>Kept: the refresh token (sealed), the manager and artist the portal's addresses need, and the
 * fingerprint made up for this band. Never kept: the password, nor the authenticator secret — they stay
 * with the band.
 */
@Component
@OnPlatformClient(platform = "bandsintown", value = PlatformClient.HTTP)
class HttpBitSignIn implements PlatformSignIn {

    private final String baseUrl;
    private final Clock clock;

    HttpBitSignIn(BandsintownProperties properties, Clock clock) {
        this.baseUrl = properties.baseUrl();
        this.clock = clock;
    }

    @Override
    public Platform platform() {
        return BandsintownPlatform.PLATFORM;
    }

    @Override
    public Kept signIn(PlatformAccount typed) {
        BitConnection connection;
        try {
            connection = BitConnection.read(typed.secret(BandsintownAccount.CONNECTION));
            new BitSignIn(baseUrl, connection, clock).sessionToken();
        } catch (BitPortalException e) {
            return Kept.failed(e.getMessage());
        }
        Map<String, String> settings = new LinkedHashMap<>(typed.settings());
        settings.put(BandsintownAccount.MANAGER_ID, connection.managerId());
        settings.put(BandsintownAccount.ACTOR_ID, connection.actorId());
        settings.put(BandsintownAccount.FINGERPRINT, connection.fingerprint());
        return Kept.of(settings, Map.of(BandsintownAccount.REFRESH_TOKEN, connection.refreshToken()));
    }
}
