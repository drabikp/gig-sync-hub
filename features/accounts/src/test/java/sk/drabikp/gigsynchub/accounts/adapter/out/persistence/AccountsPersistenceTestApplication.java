package sk.drabikp.gigsynchub.accounts.adapter.out.persistence;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Bean;
import sk.drabikp.gigsynchub.accounts.application.AccountsFakes;
import sk.drabikp.gigsynchub.accounts.application.AesGcmSecretBox;
import sk.drabikp.gigsynchub.accounts.application.port.in.SecretBox;

import java.time.Clock;

/** Boots only this module's persistence for its tests (the schema comes from the gig module's migrations). */
@SpringBootApplication
@TestComponent
class AccountsPersistenceTestApplication {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    SecretBox secretBox() {
        return new AesGcmSecretBox(AccountsFakes.TEST_KEYS);
    }
}
