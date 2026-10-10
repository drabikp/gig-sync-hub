package sk.drabikp.bzscraper.calendar.adapter.out.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import sk.drabikp.bzscraper.accounts.application.AccountsFakes;
import sk.drabikp.bzscraper.accounts.application.AesGcmSecretBox;
import sk.drabikp.bzscraper.calendar.domain.BandCalendar;
import sk.drabikp.bzscraper.gig.domain.TestBands;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static sk.drabikp.bzscraper.gig.domain.TestBands.BAND;
import static sk.drabikp.bzscraper.gig.domain.TestBands.OTHER;

@SpringBootTest
@Transactional
class JpaBandCalendarStoreTest {

    private static final String ADDRESS = "https://calendar.example.test/ical/secret-part/basic.ics";

    @Autowired
    private JpaBandCalendarStore store;
    @Autowired
    private BandCalendarJpaRepository repository;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void anotherBand() {
        jdbc.update(TestBands.INSERT_OTHER);
    }

    @Test
    void a_bands_calendar_round_trips_its_address_sealed_in_the_row() {
        store.save(new BandCalendar(BAND, ADDRESS, ZoneId.of("Europe/Bratislava")));
        repository.flush();

        assertThat(store.of(BAND)).contains(new BandCalendar(BAND, ADDRESS, ZoneId.of("Europe/Bratislava")));
        assertThat(store.of(OTHER)).isEmpty();
        byte[] row = jdbc.queryForObject("select ical_url from band_calendar where band_id = ?", byte[].class,
                BAND.value());
        assertThat(new String(row, StandardCharsets.ISO_8859_1)).doesNotContain("secret-part", "calendar.example");
    }

    @Test
    void a_sealed_address_copied_to_another_band_doesnt_open_there() {
        store.save(new BandCalendar(BAND, ADDRESS, ZoneId.of("Europe/Prague")));
        repository.flush();
        jdbc.update("insert into band_calendar (band_id, ical_url, key_id, zone, updated_at) "
                + "select ?, ical_url, key_id, zone, updated_at from band_calendar where band_id = ?",
                OTHER.value(), BAND.value());

        assertThatThrownBy(() -> store.of(OTHER)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void after_a_new_key_every_address_is_sealed_again_with_it_and_deleting_forgets_it() {
        store.save(new BandCalendar(BAND, ADDRESS, ZoneId.of("Europe/Prague")));
        repository.flush();
        String newKey = "next:" + Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
        JpaBandCalendarStore rotated = new JpaBandCalendarStore(repository,
                new AesGcmSecretBox(newKey + "," + AccountsFakes.TEST_KEYS), Clock.systemUTC());

        assertThat(rotated.reseal()).isEqualTo(1);
        repository.flush();
        assertThat(new JpaBandCalendarStore(repository, new AesGcmSecretBox(newKey), Clock.systemUTC()).of(BAND)
                .orElseThrow().address()).isEqualTo(ADDRESS);

        store.delete(BAND);
        assertThat(store.of(BAND)).isEmpty();
    }
}
