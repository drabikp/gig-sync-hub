package sk.drabikp.gigsynchub.accounts.adapter.out.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import sk.drabikp.gigsynchub.accounts.application.AccountsFakes;
import sk.drabikp.gigsynchub.accounts.application.AesGcmSecretBox;
import sk.drabikp.gigsynchub.accounts.domain.AccountCheck;
import sk.drabikp.gigsynchub.accounts.domain.Band;
import sk.drabikp.gigsynchub.accounts.domain.PlatformAccount;
import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.gig.domain.platform.Platform;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class JpaPlatformAccountStoreTest {

    private static final Platform ALPHA = Platform.of("ALPHA");

    @Autowired
    private JpaPlatformAccountStore store;
    @Autowired
    private JpaAccountsStores bands;
    @Autowired
    private PlatformAccountJpaRepository repository;
    @Autowired
    private JdbcTemplate jdbc;

    private final BandId other = new BandId(UUID.randomUUID());

    private PlatformAccount account(BandId band) {
        return new PlatformAccount(band, ALPHA, Map.of("login", "band@x.test"),
                Map.of("password", "hunter2", "code", "JBSWY3DP"), null);
    }

    @Test
    void an_account_round_trips_its_secrets_sealed_in_the_row() {
        store.save(account(BandId.FIRST).checked(new AccountCheck(false, "Wrong password",
                Instant.parse("2026-10-10T10:00:00Z"))));
        repository.flush();

        PlatformAccount read = store.of(BandId.FIRST, ALPHA).orElseThrow();
        assertThat(read.settings()).isEqualTo(Map.of("login", "band@x.test"));
        assertThat(read.secrets()).isEqualTo(Map.of("password", "hunter2", "code", "JBSWY3DP"));
        assertThat(read.lastCheck()).isEqualTo(new AccountCheck(false, "Wrong password",
                Instant.parse("2026-10-10T10:00:00Z")));
        byte[] row = jdbc.queryForObject("select secrets from band_platform_account where band_id = ?",
                byte[].class, BandId.FIRST.value());
        assertThat(new String(row, StandardCharsets.ISO_8859_1)).doesNotContain("hunter2", "JBSWY3DP");
    }

    @Test
    void a_sealed_row_copied_to_another_band_doesnt_open_there() {
        bands.save(new Band(other, "other-" + other.value().toString().substring(0, 8), "Other"));
        store.save(account(BandId.FIRST));
        repository.flush();
        jdbc.update("insert into band_platform_account (band_id, platform, settings, secrets, key_id, updated_at) "
                + "select ?, platform, settings, secrets, key_id, updated_at from band_platform_account "
                + "where band_id = ?", other.value(), BandId.FIRST.value());

        assertThat(store.ofBand(BandId.FIRST)).hasSize(1);
        assertThatThrownBy(() -> store.of(other, ALPHA)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void after_a_new_key_every_account_is_sealed_again_with_it() {
        store.save(account(BandId.FIRST));
        String newKey = "next:" + Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
        JpaPlatformAccountStore rotated = new JpaPlatformAccountStore(repository,
                new AesGcmSecretBox(newKey + "\n" + AccountsFakes.TEST_KEYS), Clock.systemUTC());

        assertThat(rotated.reseal()).isEqualTo(1);
        repository.flush();
        assertThat(rotated.reseal()).isZero();
        assertThat(jdbc.queryForObject("select key_id from band_platform_account where band_id = ?", String.class,
                BandId.FIRST.value())).isEqualTo("next");
        assertThat(new JpaPlatformAccountStore(repository, new AesGcmSecretBox(newKey), Clock.systemUTC())
                .of(BandId.FIRST, ALPHA).orElseThrow().secret("password")).isEqualTo("hunter2");
    }

    /**
     * A rotation is every band's rows, and it runs while the app starts, so it walks them a page at
     * a time rather than holding the table. The page size is this test's to set: with the real one
     * (200) a single page would hold everything here and the walk would never be exercised at all.
     */
    @Test
    void a_rotation_walks_past_the_end_of_a_page() {
        for (int i = 0; i < 5; i++) {
            BandId band = new BandId(UUID.randomUUID());
            bands.save(new Band(band, "roll-" + band.value().toString().substring(0, 8), "Rolling"));
            store.save(account(band));
        }
        repository.flush();
        String newKey = "next:" + Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
        JpaPlatformAccountStore rotated = new JpaPlatformAccountStore(repository,
                new AesGcmSecretBox(newKey + "\n" + AccountsFakes.TEST_KEYS), Clock.systemUTC(), 2);

        assertThat(rotated.reseal()).as("all five, in pages of two").isEqualTo(5);
        repository.flush();
        assertThat(jdbc.queryForObject("select count(*) from band_platform_account where key_id <> 'next'",
                Integer.class)).isZero();
    }

    @Test
    void forgetting_an_account_removes_its_row() {
        store.save(account(BandId.FIRST));
        store.delete(BandId.FIRST, ALPHA);
        assertThat(store.of(BandId.FIRST, ALPHA)).isEmpty();
    }
}
