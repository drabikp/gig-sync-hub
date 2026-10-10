package sk.drabikp.bzscraper.bandzone;

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

import java.util.Map;

/**
 * Bandzone.cz as the core knows it: a cancelled concert stays listed as cancelled, a concert is
 * the whole event (not the band's slot), the entry is shown; its details win on import over
 * other platforms'. A band's account there: its login, password and the band's address.
 */
@Configuration
public class BandzonePlatform {

    public static final Platform PLATFORM = Platform.of("BANDZONE");
    private static final Logger log = LoggerFactory.getLogger(BandzonePlatform.class);

    @Bean
    PlatformTraits bandzoneTraits() {
        return PlatformTraits.of(PLATFORM, "Bandzone")
                .keepsCancelledEvents(true)
                .listsBandSlot(false)
                .carriesAdmission(true)
                .importPrecedence(10)
                .eventUrl("https://bandzone.cz/koncert/{id}")      // redirects to the full page
                .accountField(AccountField.text(BandzoneAccount.LOGIN, true, "Login", "The e-mail you sign in to Bandzone with"))
                .accountField(AccountField.secret(BandzoneAccount.PASSWORD, true, "Password", null))
                .accountField(AccountField.text(BandzoneAccount.BAND_SLUG, true, "The band's address",
                        "bandzone.cz/<this> — the band's page on Bandzone"))
                .build();
    }

    /**
     * Band #1's account from the configuration ({@code bzscraper.bandzone.login / password / band-slug},
     * from before each band connected its own), kept once while band #1 has none.
     */
    @Bean
    ApplicationRunner bandzoneAccountOfBandOne(BandzoneProperties properties, PlatformAccounts accounts) {
        return args -> {
            if (properties.login().isBlank()) {
                return;
            }
            try {
                if (accounts.adopt(BandId.FIRST, PLATFORM,
                        Map.of(BandzoneAccount.LOGIN, properties.login(), BandzoneAccount.BAND_SLUG, properties.bandSlug()),
                        Map.of(BandzoneAccount.PASSWORD, properties.password()))) {
                    log.info("Band #1's Bandzone account kept from the configuration — managed on the page from now on");
                }
            } catch (UserFacingException e) {
                log.warn("Band #1's Bandzone account from the configuration not kept: {}", e.getMessage());
            }
        };
    }
}
