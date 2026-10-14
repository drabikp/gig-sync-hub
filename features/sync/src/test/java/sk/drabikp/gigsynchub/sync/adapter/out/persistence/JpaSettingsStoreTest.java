package sk.drabikp.gigsynchub.sync.adapter.out.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import sk.drabikp.gigsynchub.gig.domain.TestBands;

import static org.assertj.core.api.Assertions.assertThat;
import static sk.drabikp.gigsynchub.gig.domain.TestBands.BAND;
import static sk.drabikp.gigsynchub.gig.domain.TestBands.OTHER;

@SpringBootTest
@Transactional
class JpaSettingsStoreTest {

    @Autowired
    private JpaSettingsStore settings;

    @Autowired
    private JdbcTemplate jdbc;

    /** The second band: {@code band_setting.band_id} references it, so it has to exist here. */
    @BeforeEach
    void anotherBand() {
        jdbc.update(TestBands.INSERT_OTHER);
    }

    @Test
    void keeps_replaces_and_removes_a_setting() {
        assertThat(settings.get(BAND, "sync.paused")).isEmpty();

        settings.put(BAND, "sync.paused", "true");
        settings.put(BAND, "sync.paused", "false");
        assertThat(settings.get(BAND, "sync.paused")).contains("false");

        settings.put(BAND, "sync.paused", null);
        assertThat(settings.get(BAND, "sync.paused")).isEmpty();
    }

    @Test
    void a_setting_is_one_bands_and_the_other_band_has_its_own() {
        settings.put(BAND, "sync.paused", "true");

        assertThat(settings.get(OTHER, "sync.paused")).as("one band's pause is not another's").isEmpty();

        settings.put(OTHER, "sync.paused", "false");
        assertThat(settings.get(BAND, "sync.paused")).contains("true");
        assertThat(settings.get(OTHER, "sync.paused")).contains("false");

        settings.put(BAND, "sync.paused", null);
        assertThat(settings.get(OTHER, "sync.paused")).as("removing one leaves the other").contains("false");
    }

    /**
     * The one read here that is no band's: the worker needs every band's pause at once, to know whose
     * work it may not start. It is asked for by name ({@code @AcrossBands}) and must answer each band's
     * own value, keyed by band — the single place where seeing across bands is the point.
     */
    @Test
    void every_bands_pause_is_read_at_once_each_against_its_own_band() {
        settings.put(BAND, "sync.paused", "true");
        settings.put(OTHER, "sync.paused", "false");

        assertThat(settings.all("sync.paused"))
                .containsEntry(BAND, "true")
                .containsEntry(OTHER, "false");
        assertThat(settings.all("sync.nothing-of-the-sort")).isEmpty();
    }
}
