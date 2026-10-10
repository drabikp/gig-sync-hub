package sk.drabikp.bzscraper.sync.application.engine;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.TestGigs;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.sync.application.SyncFakes;
import sk.drabikp.bzscraper.sync.application.SyncPause;
import sk.drabikp.bzscraper.sync.domain.Lane;
import sk.drabikp.bzscraper.sync.domain.StepOutcome;
import sk.drabikp.bzscraper.sync.domain.StepType;
import sk.drabikp.bzscraper.sync.domain.SyncAction;
import sk.drabikp.bzscraper.sync.domain.SyncStatus;
import sk.drabikp.bzscraper.sync.domain.SyncTask;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static sk.drabikp.bzscraper.gig.domain.TestBands.BAND;
import static sk.drabikp.bzscraper.gig.domain.platform.TestPlatforms.BANDSINTOWN;
import static sk.drabikp.bzscraper.gig.domain.platform.TestPlatforms.BANDZONE;

/** The worker's entry: the next due task to the engine; settling what a restart interrupted. */
class SyncDispatcherTest {

    private final SyncFakes.Outbox outbox = new SyncFakes.Outbox();
    private final SyncFakes.Published published = new SyncFakes.Published();
    private final SyncFakes.Gigs gigs = new SyncFakes.Gigs();
    private final SyncFakes.Signals signals = new SyncFakes.Signals();
    private final SyncFakes.MutableClock clock = new SyncFakes.MutableClock();
    private final SyncFakes.Step bzRemove = new SyncFakes.Step(BANDZONE, StepType.REMOVE, 1);
    private final SyncDispatcher dispatcher = SyncFakes.dispatcher(SyncFakes.engine(
            SyncFakes.registry(published, bzRemove), gigs, published, outbox, signals, clock), outbox, signals, clock);

    private final Gig gig = TestGigs.gig("Fest", "Klub 007");

    private SyncTask queue(Gig g, Platform platform, SyncAction action) {
        return outbox.enqueue(BAND, g.id(), SyncTask.labelOf(g), platform, action, clock.instant());
    }

    @Test
    void the_next_due_task_runs_until_nothing_is_due() {
        published.record(BAND, BANDZONE, gig.id(), "100");
        SyncTask delete = queue(gig, BANDZONE, SyncAction.DELETE);

        assertThat(dispatcher.runNext()).isTrue();
        assertThat(dispatcher.runNext()).isFalse();

        assertThat(outbox.find(delete.id()).orElseThrow().status()).isEqualTo(SyncStatus.DONE);
        assertThat(bzRemove.ran()).singleElement().satisfies(i -> assertThat(i.externalRef()).isEqualTo("100"));
    }

    @Test
    void a_bands_platform_that_keeps_failing_is_held_back_while_the_others_go_on_then_tried_again() {
        SyncFakes.Step bitRemove = new SyncFakes.Step(BANDSINTOWN, StepType.REMOVE, 1);
        bitRemove.outcome = item -> StepOutcome.failed("login page did not load");
        PlatformBreakers breakers = new PlatformBreakers(2, 3, Duration.ofMinutes(30), clock, signals, signals);
        SyncPause never = new SyncPause(signals, signals, new SyncFakes.Settings());
        SyncDispatcher guarded = SyncFakes.dispatcher(SyncFakes.engine(SyncFakes.registry(published, bzRemove, bitRemove),
                gigs, published, outbox, signals, clock, never, breakers), outbox, signals, clock, never, breakers);
        List<Gig> onBit = List.of(TestGigs.gig("A", "Klub A"), TestGigs.gig("B", "Klub B"), TestGigs.gig("C", "Klub C"));
        onBit.forEach(g -> {
            published.record(BAND, BANDSINTOWN, g.id(), "bit-" + g.title());
            queue(g, BANDSINTOWN, SyncAction.DELETE);
        });
        published.record(BAND, BANDZONE, gig.id(), "100");
        SyncTask onBandzone = queue(gig, BANDZONE, SyncAction.DELETE);

        while (guarded.runNext()) {
            // until nothing is due on a platform that isn't held back
        }

        assertThat(bitRemove.ran()).as("held back after two failures in a row").hasSize(2);
        assertThat(outbox.find(onBandzone.id()).orElseThrow().status()).isEqualTo(SyncStatus.DONE);
        assertThat(breakers.breakers(BAND)).singleElement().satisfies(b -> {
            assertThat(b.platform()).isEqualTo(BANDSINTOWN);
            assertThat(b.held(clock.instant())).isTrue();
            assertThat(b.lastFailure()).contains("login page did not load");
        });

        clock.advance(Duration.ofMinutes(31));                       // the trial: one more failure holds it again
        guarded.runNext();
        assertThat(bitRemove.ran()).hasSize(3);
        assertThat(breakers.held(new Lane(BAND, BANDSINTOWN))).isTrue();

        bitRemove.outcome = item -> StepOutcome.done(null);
        breakers.resume(BAND, BANDSINTOWN);                                // the user fixed it
        clock.advance(Duration.ofMinutes(20));                       // the failed ones are due again
        while (guarded.runNext()) {
            // the rest runs
        }
        assertThat(breakers.breakers(BAND)).isEmpty();
        assertThat(outbox.all()).allSatisfy(t -> assertThat(t.status()).isEqualTo(SyncStatus.DONE));
    }

    @Test
    void a_discarded_task_is_not_run() {
        published.record(BAND, BANDZONE, gig.id(), "100");
        SyncTask task = queue(gig, BANDZONE, SyncAction.DELETE);
        outbox.discard(task.id(), clock.instant());

        assertThat(dispatcher.runNext()).isFalse();
        assertThat(bzRemove.calls).isEmpty();
    }

    @Test
    void after_a_restart_interrupted_safe_work_runs_again_and_an_interrupted_publish_waits_for_the_user() {
        SyncTask delete = queue(gig, BANDSINTOWN, SyncAction.DELETE);
        SyncTask publish = queue(TestGigs.gig("Other", "Barrák"), BANDZONE, SyncAction.PUBLISH);
        outbox.markRunning(delete.id(), clock.instant());
        outbox.markRunning(publish.id(), clock.instant());

        dispatcher.recoverInterrupted();

        assertThat(outbox.find(delete.id()).orElseThrow().status()).isEqualTo(SyncStatus.PENDING);
        assertThat(outbox.find(publish.id()).orElseThrow().status()).isEqualTo(SyncStatus.FAILED);
        assertThat(outbox.find(publish.id()).orElseThrow().message()).contains("check there, then Retry or Discard");
    }

    @Test
    void a_paused_sync_starts_nothing_until_resumed() {
        published.record(BAND, BANDZONE, gig.id(), "100");
        queue(gig, BANDZONE, SyncAction.DELETE);
        SyncPause pause = new SyncPause(signals, signals, new SyncFakes.Settings());
        SyncDispatcher pausable = SyncFakes.dispatcher(SyncFakes.engine(SyncFakes.registry(published, bzRemove), gigs,
                published, outbox, signals, clock, pause, PlatformBreakers.never(clock)), outbox, signals, clock, pause,
                PlatformBreakers.never(clock));

        pause.pause(BAND);
        assertThat(pausable.runNext()).isFalse();
        assertThat(bzRemove.calls).isEmpty();

        pause.resume(BAND);
        assertThat(pausable.runNext()).isTrue();
        assertThat(signals.wakes).as("resuming wakes the worker").isPositive();
    }
}
