package sk.drabikp.bzscraper.accounts.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import sk.drabikp.bzscraper.accounts.application.AccountsService;
import sk.drabikp.bzscraper.accounts.application.AesGcmSecretBox;
import sk.drabikp.bzscraper.accounts.application.PlatformAccountDirectory;
import sk.drabikp.bzscraper.accounts.application.PlatformAccountService;
import sk.drabikp.bzscraper.accounts.application.port.in.SecretBox;
import sk.drabikp.bzscraper.accounts.application.port.out.BandStore;
import sk.drabikp.bzscraper.accounts.application.port.out.CurrentLogin;
import sk.drabikp.bzscraper.accounts.application.port.out.MembershipStore;
import sk.drabikp.bzscraper.accounts.application.port.out.PlatformAccountCheck;
import sk.drabikp.bzscraper.accounts.application.port.out.PlatformAccountStore;
import sk.drabikp.bzscraper.accounts.application.port.out.UserStore;
import sk.drabikp.bzscraper.gig.application.port.out.Transactions;
import sk.drabikp.bzscraper.gig.domain.platform.Platforms;

import java.time.Clock;

/**
 * Users, bands and memberships ({@link AccountsService}); the bands' platform accounts
 * ({@link PlatformAccountDirectory} for the platforms, {@link PlatformAccountService} for the page) and the
 * box their secrets are sealed in. On start: names the first band, seals again what an older key sealed.
 */
@Configuration
class AccountsConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AccountsConfiguration.class);

    @Bean
    AccountsService accountsService(CurrentLogin login, UserStore users, BandStore bands, MembershipStore memberships,
                                    Transactions transactions, AccountsProperties accounts) {
        return new AccountsService(login, users, bands, memberships, transactions, accounts.firstOwnerEmail());
    }

    @Bean
    SecretBox secretBox(AccountsProperties accounts) {
        SecretBox box = new AesGcmSecretBox(accounts.dataKeys());
        if (!box.ready()) {
            log.warn("No bzscraper.accounts.data-keys: the bands' platform accounts and calendars can't be kept");
        }
        return box;
    }

    @Bean
    PlatformAccountDirectory platformAccountDirectory(PlatformAccountStore store, Platforms platforms,
                                                      SecretBox box) {
        return new PlatformAccountDirectory(store, platforms, box);
    }

    /** The platforms' sign-in tests are looked up when a test runs: they use the accounts themselves. */
    @Bean
    PlatformAccountService platformAccountService(PlatformAccountStore store, Platforms platforms,
                                                  AccountsService access, SecretBox box,
                                                  ObjectProvider<PlatformAccountCheck> checks, Clock clock) {
        return new PlatformAccountService(store, platforms, access, box, () -> checks.orderedStream().toList(),
                clock);
    }

    /** The first band's name and address from the configuration, once (while it has migration V2's). */
    @Bean
    ApplicationRunner nameFirstBand(AccountsService accounts, AccountsProperties properties) {
        return args -> accounts.nameFirstBand(properties.firstBandSlug(), properties.firstBandName());
    }

    /** After a new key was put first: every account sealed again with it (then the old key can go). */
    @Bean
    ApplicationRunner resealPlatformAccounts(PlatformAccountDirectory accounts) {
        return args -> {
            int resealed = accounts.reseal();
            if (resealed > 0) {
                log.info("Sealed {} platform accounts again with the active data key", resealed);
            }
        };
    }
}
