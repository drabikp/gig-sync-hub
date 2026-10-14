package sk.drabikp.gigsynchub.sync;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import sk.drabikp.gigsynchub.accounts.application.port.out.BandStore;
import sk.drabikp.gigsynchub.accounts.domain.Band;
import sk.drabikp.gigsynchub.gig.application.port.out.Transactions;
import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.gig.domain.GigId;
import sk.drabikp.gigsynchub.gig.domain.platform.Platform;
import sk.drabikp.gigsynchub.gig.domain.platform.Platforms;
import sk.drabikp.gigsynchub.sync.application.port.out.SyncOutbox;
import sk.drabikp.gigsynchub.sync.domain.SyncAction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What it COSTS to decide whose turn it is, counted rather than assumed.
 *
 * <p>The worker asks that question once per workload and keeps asking until nothing is due, so
 * whatever it costs is paid once per piece of work — and it used to be "read every band's due rows",
 * which made draining a backlog of N tasks cost N× the whole backlog. Ten bands with a hundred tasks
 * each put a hundred thousand rows through Hibernate to do a thousand pieces of work, on two threads.
 *
 * <p>The promise is not a particular number but that the number does not FOLLOW the backlog: asking
 * with a hundred tasks waiting must cost what asking with ten does. That is the whole difference
 * between a cost that is linear in the work and one that is quadratic in the queue, and it is exactly
 * the shape {@code GigListCostsTest} guards for the gig list and nothing guarded here.
 */
@SpringBootTest
class DecidingWhoseTurnItIsCostsTest {

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

    /** Deleting the band takes its tasks and their log with it — the schema's own cascade. */
    @AfterEach
    void removeTheBands() {
        transactions.inTransaction(() -> made.forEach(bands::delete));
    }

    private BandId aBand() {
        BandId band = new BandId(UUID.randomUUID());
        String slug = "cost-" + band.value().toString().substring(0, 8);
        transactions.inTransaction(() -> bands.save(new Band(band, slug, "Cost " + slug)));
        made.add(band);
        return band;
    }

    private Platform somewhere() {
        return platforms.all().getFirst();
    }

    /** One band's queue, as deep as asked for; every task is its own gig, so none supersedes another. */
    private void queue(BandId band, int tasks) {
        transactions.inTransaction(() -> {
            for (int i = 0; i < tasks; i++) {
                outbox.enqueue(band, GigId.fresh(), "cost gig", somewhere(), SyncAction.UPDATE, NOW);
            }
        });
    }

    /** What one "whose turn is it, and what shall I run" question reads. */
    private long rowsReadDeciding(BandId band) {
        long before = statistics.getEntityLoadCount();
        transactions.inTransaction(() -> {
            outbox.bandsWithWorkDue(NOW);
            outbox.due(band, NOW, 200);
        });
        return statistics.getEntityLoadCount() - before;
    }

    @Test
    void asking_whose_turn_it_is_does_not_cost_more_because_other_bands_are_busy() {
        BandId mine = aBand();
        queue(mine, 5);
        long withALittleWaiting = rowsReadDeciding(mine);

        for (int band = 0; band < 5; band++) {
            queue(aBand(), 40);
        }
        long withTwoHundredMoreWaiting = rowsReadDeciding(mine);

        assertThat(withTwoHundredMoreWaiting)
                .as("deciding read %d rows with 5 tasks waiting and %d with 205 — it is following the "
                                + "backlog, not the work. The bands with work due must be asked for as bands "
                                + "(one row each), and only the band whose turn it is read for its tasks",
                        withALittleWaiting, withTwoHundredMoreWaiting)
                .isEqualTo(withALittleWaiting);
    }

    @Test
    void a_bands_own_window_is_bounded_however_deep_its_queue() {
        BandId mine = aBand();
        queue(mine, 250);

        long read = rowsReadDeciding(mine);

        assertThat(read).as("one band's queue is read only as far as it can decide anything")
                .isLessThanOrEqualTo(200);
    }
}
