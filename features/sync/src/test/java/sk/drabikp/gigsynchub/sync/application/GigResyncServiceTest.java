package sk.drabikp.gigsynchub.sync.application;

import org.junit.jupiter.api.Test;
import sk.drabikp.gigsynchub.gig.domain.Gig;
import sk.drabikp.gigsynchub.gig.domain.TestGigs;
import sk.drabikp.gigsynchub.sync.domain.QueueResult;
import sk.drabikp.gigsynchub.sync.domain.SyncAction;
import sk.drabikp.gigsynchub.sync.domain.SyncTask;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static sk.drabikp.gigsynchub.gig.domain.TestBands.BAND;
import static sk.drabikp.gigsynchub.gig.domain.platform.TestPlatforms.BANDZONE;

class GigResyncServiceTest {

    private final SyncFakes.Outbox outbox = new SyncFakes.Outbox();
    private final SyncFakes.Published published = new SyncFakes.Published();
    private final SyncFakes.Gigs gigs = new SyncFakes.Gigs();
    private final SyncFakes.Signals signals = new SyncFakes.Signals();
    private final GigResyncService service = new GigResyncService(gigs,
            SyncFakes.requests(outbox, published, signals, new SyncFakes.MutableClock()), SyncFakes.state(outbox),
            new SyncFakes.DirectTransactions());

    @Test
    void queues_an_update_where_the_gig_is_published_and_says_which_gigs_had_nothing_to_do() {
        Gig onBandzone = TestGigs.gig("On Bandzone", "Klub 007");
        Gig local = TestGigs.gig("Local", "Barrák");
        gigs.save(BAND, onBandzone);
        gigs.save(BAND, local);
        published.record(BAND, BANDZONE, onBandzone.id(), "100");

        QueueResult result = service.resync(BAND, List.of(onBandzone.id(), local.id()));

        assertThat(result.queued()).extracting(SyncTask::action).containsExactly(SyncAction.UPDATE);
        assertThat(result.notQueued()).singleElement().asString().startsWith("Local:");
        assertThat(signals.wakes).isEqualTo(1);
    }
}
