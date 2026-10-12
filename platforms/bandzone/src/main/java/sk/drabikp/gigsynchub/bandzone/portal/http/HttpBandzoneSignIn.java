package sk.drabikp.gigsynchub.bandzone.portal.http;

import org.springframework.stereotype.Component;
import sk.drabikp.gigsynchub.accounts.application.port.out.PlatformSignIn;
import sk.drabikp.gigsynchub.accounts.domain.PlatformAccount;
import sk.drabikp.gigsynchub.bandzone.BandzoneAccount;
import sk.drabikp.gigsynchub.bandzone.BandzonePlatform;
import sk.drabikp.gigsynchub.bandzone.BandzonePortalException;
import sk.drabikp.gigsynchub.bandzone.BandzoneProperties;
import sk.drabikp.gigsynchub.portal.OnPlatformClient;
import sk.drabikp.gigsynchub.portal.PlatformClient;
import sk.drabikp.gigsynchub.gig.domain.platform.Platform;

import java.util.Map;

/**
 * What is kept when a band connects Bandzone, with no browser anywhere: the password it typed is used ONCE,
 * here, to sign in ("stay signed in"), and what is written down is the session Bandzone gave back — which
 * lasts years. So the server holds something the band can revoke by signing out there, never its password.
 * Only while this server drives Bandzone over HTTP ({@code gigsynchub.bandzone.client=http}); driving it in a
 * browser needs the password itself, and then the account is kept as the band typed it.
 */
@Component
@OnPlatformClient(platform = "bandzone", value = PlatformClient.HTTP)
class HttpBandzoneSignIn implements PlatformSignIn {

    private final String baseUrl;

    HttpBandzoneSignIn(BandzoneProperties properties) {
        this.baseUrl = properties.baseUrl();
    }

    @Override
    public Platform platform() {
        return BandzonePlatform.PLATFORM;
    }

    @Override
    public Kept signIn(PlatformAccount typed) {
        BandzoneWeb web = new BandzoneWeb(baseUrl);
        try {
            web.logIn(typed.setting(BandzoneAccount.LOGIN), typed.secret(BandzoneAccount.PASSWORD));
        } catch (BandzonePortalException e) {
            return Kept.failed(e.getMessage());
        }
        String session = web.session();
        if (session.isBlank()) {
            return Kept.failed("it signed in but gave nothing to stay signed in with.");
        }
        return Kept.of(typed.settings(), Map.of(BandzoneAccount.SESSION, session));
    }
}
