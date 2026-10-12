package sk.drabikp.gigsynchub.sync.adapter.out.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static sk.drabikp.gigsynchub.gig.domain.TestBands.BAND;

@SpringBootTest
@Transactional
class JpaSettingsStoreTest {

    @Autowired
    private JpaSettingsStore settings;

    @Test
    void keeps_replaces_and_removes_a_setting() {
        assertThat(settings.get(BAND, "sync.paused")).isEmpty();

        settings.put(BAND, "sync.paused", "true");
        settings.put(BAND, "sync.paused", "false");
        assertThat(settings.get(BAND, "sync.paused")).contains("false");

        settings.put(BAND, "sync.paused", null);
        assertThat(settings.get(BAND, "sync.paused")).isEmpty();
    }
}
