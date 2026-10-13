package sk.drabikp.gigsynchub.sync;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import sk.drabikp.gigsynchub.accounts.application.port.out.BandStore;
import sk.drabikp.gigsynchub.accounts.domain.Band;
import sk.drabikp.gigsynchub.gig.application.port.out.Transactions;
import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.gig.domain.GigId;
import sk.drabikp.gigsynchub.gig.domain.platform.Platforms;
import sk.drabikp.gigsynchub.sync.application.port.out.SyncOutbox;
import sk.drabikp.gigsynchub.sync.domain.SyncAction;
import sk.drabikp.gigsynchub.sync.domain.SyncCounts;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Four numbers cost four numbers, not every row they are counted from.
 *
 * <p>The Activity page's heading and the operator's gauges both ask what a band has queued,
 * running, retrying and failed. Both used to be answered by reading every open task and counting
 * them in Java: a band with a thousand queued gigs paid a thousand rows for a heading, and the
 * gauges paid every band's, once a minute, for ever. The numbers are the database's to work out.
 *
 * <p>What is asserted is not a number of milliseconds but that nothing is LOADED: a count that goes
 * through entities will show here the moment someone writes one.
 */
@SpringBootTest
class CountingOpenWorkCostsTest {

    private static final Instant NOW = Instant.parse("2026-10-13T10:00:00Z");

    @Autowired
    private SyncOutbox outbox;
    @Autowired
    private Transactions transactions;
    @Autowired
    private Platforms platforms;
    @Autowired
    private BandStore bands;
    @Autowired
    private SessionFactory sessions;

    private Statistics statistics;
    private final List<BandId> made = new ArrayList<>();

    @BeforeEach
    void countingFromNow() {
        statistics = sessions.getStatistics();
        statistics.setStatisticsEnabled(true);
    }

    @AfterEach
    void removeTheBands() {
        transactions.inTransaction(() -> made.forEach(bands::delete));
    }

    private BandId aBand() {
        BandId band = new BandId(UUID.randomUUID());
        String slug = "count-" + band.value().toString().substring(0, 8);
        transactions.inTransaction(() -> bands.save(new Band(band, slug, "Count " + slug)));
        made.add(band);
        return band;
    }

    private void queue(BandId band, int tasks) {
        transactions.inTransaction(() -> {
            for (int i = 0; i < tasks; i++) {
                outbox.enqueue(band, GigId.fresh(), "cost gig", platforms.all().getFirst(), SyncAction.UPDATE, NOW);
            }
        });
    }

    @Test
    @DisplayName("counting a band's open work reads none of it")
    void counting_a_bands_open_work_reads_none_of_it() {
        BandId band = aBand();
        queue(band, 120);

        long before = statistics.getEntityLoadCount();
        Map<BandId, SyncCounts> counted = transactions.computeInTransaction(() -> outbox.openWork(List.of(band)));
        long loaded = statistics.getEntityLoadCount() - before;

        assertThat(counted.get(band).queued()).isEqualTo(120);
        assertThat(loaded).as("counting 120 tasks loaded %d of them; the count belongs in the query", loaded)
                .isZero();
    }

    @Test
    @DisplayName("every band's numbers are one read, not one read a band")
    void every_bands_numbers_are_one_read_not_one_read_a_band() {
        List<BandId> five = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            BandId band = aBand();
            queue(band, 3);
            five.add(band);
        }

        long before = statistics.getPrepareStatementCount();
        Map<BandId, SyncCounts> counted = transactions.computeInTransaction(() -> outbox.openWork(five));
        long statements = statistics.getPrepareStatementCount() - before;

        assertThat(counted).hasSize(5);
        assertThat(counted.values()).allSatisfy(c -> assertThat(c.queued()).isEqualTo(3));
        assertThat(statements).as("five bands took %d statements", statements).isEqualTo(1);
    }
}
