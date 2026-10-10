package sk.drabikp.bzscraper.bandsintown;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import sk.drabikp.bzscraper.accounts.application.port.in.PlatformAccounts;
import sk.drabikp.bzscraper.gig.application.UserFacingException;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.platform.AccountField;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.gig.domain.platform.PlatformTraits;

import java.util.HashMap;
import java.util.Map;

/**
 * Bandsintown as the core knows it: no cancelled state (cancelling removes the event), an
 * event is when the artist plays (the band's slot of a longer event), no entry information.
 * A band's account there: the artist portal's login (with its authenticator secret) and the artist.
 */
@Configuration
public class BandsintownPlatform {

    public static final Platform PLATFORM = Platform.of("BANDSINTOWN");
    private static final Logger log = LoggerFactory.getLogger(BandsintownPlatform.class);

    @Bean
    PlatformTraits bandsintownTraits() {
        return PlatformTraits.of(PLATFORM, "Bandsintown")
                .keepsCancelledEvents(false)                       // cancelling removes the event
                .listsBandSlot(true)
                .carriesAdmission(false)
                .importPrecedence(20)
                .eventUrl("https://www.bandsintown.com/e/{id}")
                .accountField(AccountField.text(BandsintownAccount.LOGIN, true, "Login",
                        "The e-mail you sign in to artists.bandsintown.com with"))
                .accountField(AccountField.secret(BandsintownAccount.PASSWORD, true, "Password", null))
                .accountField(AccountField.secret(BandsintownAccount.TOTP_SECRET, false, "Authenticator secret",
                        "The text secret of two-factor sign-in (base32, shown when setting up the authenticator app)"
                                + " — needed when Bandsintown asks for a code"))
                .accountField(AccountField.text(BandsintownAccount.ARTIST_NAME, true, "Artist name",
                        "As Bandsintown spells it — every uploaded event carries it"))
                .accountField(AccountField.text(BandsintownAccount.ARTIST_ID, false, "Artist id",
                        "Only when the login manages several artists: the number in artists.bandsintown.com/artists/<id>"))
                .accountField(AccountField.flag(BandsintownAccount.NOTIFY_FOLLOWERS, "Notify followers",
                        "Off: new events are published without telling the followers"))
                .build();
    }

    /**
     * Band #1's account from the configuration ({@code bzscraper.bandsintown.login / password / totp-secret /
     * artist-name / artist-id / notify-followers}, from before each band connected its own), kept once while
     * band #1 has none.
     */
    @Bean
    ApplicationRunner bandsintownAccountOfBandOne(BandsintownProperties properties, PlatformAccounts accounts) {
        return args -> {
            if (properties.login().isBlank()) {
                return;
            }
            Map<String, String> settings = new HashMap<>(Map.of(BandsintownAccount.LOGIN, properties.login(),
                    BandsintownAccount.ARTIST_NAME, properties.artistName(),
                    BandsintownAccount.NOTIFY_FOLLOWERS, String.valueOf(properties.notifyFollowers())));
            if (!properties.artistId().isBlank()) {
                settings.put(BandsintownAccount.ARTIST_ID, properties.artistId());
            }
            Map<String, String> secrets = new HashMap<>(Map.of(BandsintownAccount.PASSWORD, properties.password()));
            if (!properties.totpSecret().isBlank()) {
                secrets.put(BandsintownAccount.TOTP_SECRET, properties.totpSecret());
            }
            try {
                if (accounts.adopt(BandId.FIRST, PLATFORM, settings, secrets)) {
                    log.info("Band #1's Bandsintown account kept from the configuration — managed on the page from now on");
                }
            } catch (UserFacingException e) {
                log.warn("Band #1's Bandsintown account from the configuration not kept: {}", e.getMessage());
            }
        };
    }
}
