package sk.drabikp.bzscraper.check.adapter.out.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import sk.drabikp.bzscraper.check.domain.Drift;
import sk.drabikp.bzscraper.check.domain.PlatformCheck;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.gig.domain.TestGigs;
import sk.drabikp.bzscraper.gig.domain.TestBands;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static sk.drabikp.bzscraper.gig.domain.TestBands.BAND;
import static sk.drabikp.bzscraper.gig.domain.TestBands.OTHER;

@SpringBootTest
@Transactional
class JpaCheckResultStoreTest {

    private static final Platform ALPHA = Platform.of("ALPHA");
    private static final Platform BETA = Platform.of("BETA");

    @Autowired
    private JpaCheckResultStore store;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void anotherBand() {
        jdbc.update(TestBands.INSERT_OTHER);
    }

    @Test
    void a_bands_latest_check_round_trips_and_replaces_the_one_before() {
        GigId gig = TestGigs.id("2026-11-14|klub 007");
        PlatformCheck check = new PlatformCheck(Instant.parse("2026-10-10T04:30:00Z"),
                List.of(new Drift(ALPHA, gig, "14 Nov · Fest", "563380", Drift.Kind.DIFFERENT,
                                List.of(new Drift.Difference(Drift.Field.TIME, "20:00", "21:00", "time: 20:00 → 21:00"))),
                        new Drift(BETA, gig, "14 Nov · Fest", "99", Drift.Kind.MISSING, List.of())),
                Map.of(BETA, "held back"), Map.of(ALPHA, 2));

        store.save(BAND, new PlatformCheck(Instant.parse("2026-10-09T04:30:00Z"), List.of(), Map.of(), Map.of()));
        store.save(BAND, check);

        assertThat(store.of(BAND)).contains(check);
        assertThat(store.of(OTHER)).isEmpty();
    }
}
