package sk.drabikp.bzscraper.sync.application.engine;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.TestGigs;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.sync.application.SyncFakes;
import sk.drabikp.bzscraper.sync.application.SyncPause;
import sk.drabikp.bzscraper.sync.application.port.out.SyncStep;
import sk.drabikp.bzscraper.sync.domain.Lane;
import sk.drabikp.bzscraper.sync.domain.StepOutcome;
import sk.drabikp.bzscraper.sync.domain.StepType;
import sk.drabikp.bzscraper.sync.domain.SyncAction;
import sk.drabikp.bzscraper.sync.domain.SyncStatus;
import sk.drabikp.bzscraper.sync.domain.SyncTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static sk.drabikp.bzscraper.gig.domain.platform.TestPlatforms.BANDSINTOWN;
import static sk.drabikp.bzscraper.gig.domain.platform.TestPlatforms.BANDZONE;

/**
 * Many bands' work on one server (multi-band plan, phase 3): several workers at once, the bands taking
 * turns, one run per lane at a time, and a band whose account is broken holding back only itself.
 */
class ManyBandsSyncTest {

    private static final int WORKERS = 3;

    private final SyncFakes.Outbox outbox = new SyncFakes.Outbox();
    private final SyncFakes.Published published = new SyncFakes.Published();
    private final SyncFakes.Gigs gigs = new SyncFakes.Gigs();
    private final SyncFakes.Signals signals = new SyncFakes.Signals();
    private final SyncFakes.MutableClock clock = new SyncFakes.MutableClock();

    /** A remove step that takes a moment, watching how many lanes run at once and that no lane runs twice. */
    private static final class WatchedRemove implements SyncStep {

        final Platform platform;
        final Set<BandId> broken;
        final Map<Lane, AtomicInteger> perLane = new ConcurrentHashMap<>();
        final AtomicInteger atOnce = new AtomicInteger();
        final AtomicInteger mostAtOnce = new AtomicInteger();
        final AtomicInteger mostInOneLane = new AtomicInteger();
        final List<BandId> order = new ArrayList<>();

        WatchedRemove(Platform platform, Set<BandId> broken) {
            this.platform = platform;
            this.broken = broken;
        }

        @Override
        public Platform platform() {
            return platform;
        }

        @Override
        public StepType type() {
            return StepType.REMOVE;
        }

        @Override
        public List<StepOutcome> run(List<Item> items) {
            BandId band = items.getFirst().band();
            synchronized (order) {
                order.add(band);
            }
            int inLane = perLane.computeIfAbsent(new Lane(band, platform), l -> new AtomicInteger()).incrementAndGet();
            mostInOneLane.accumulateAndGet(inLane, Math::max);
            mostAtOnce.accumulateAndGet(atOnce.incrementAndGet(), Math::max);
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                atOnce.decrementAndGet();
                perLane.get(new Lane(band, platform)).decrementAndGet();
            }
            return items.stream().map(item -> broken.contains(item.band())
                    ? StepOutcome.failed("login failed (the band's password changed)")
                    : StepOutcome.done(null)).toList();
        }
    }

    private static BandId band(int n) {
        return new BandId(UUID.fromString("00000000-0000-0000-0000-0000000001%02d".formatted(n)));
    }

    private SyncTask queueDelete(BandId band, Platform platform, Gig gig) {
        published.record(band, platform, gig.id(), "ext-" + gig.title());
        return outbox.enqueue(band, gig.id(), SyncTask.labelOf(gig), platform, SyncAction.DELETE, clock.instant());
    }

    private void runWorkers(SyncDispatcher dispatcher) throws Exception {
        try (ExecutorService workers = Executors.newFixedThreadPool(WORKERS)) {
            List<Future<?>> running = new ArrayList<>();
            for (int i = 0; i < WORKERS; i++) {
                running.add(workers.submit(() -> {
                    while (dispatcher.runNext()) {
                        // until nothing it may run is due
                    }
                }));
            }
            for (Future<?> worker : running) {
                worker.get();
            }
        }
    }

    @Test
    void ten_bands_queued_at_once_all_finish_and_a_broken_account_holds_back_only_its_band() throws Exception {
        BandId brokenBand = band(7);
        WatchedRemove bzRemove = new WatchedRemove(BANDZONE, Set.of(brokenBand));
        WatchedRemove bitRemove = new WatchedRemove(BANDSINTOWN, Set.of());
        PlatformBreakers breakers = new PlatformBreakers(2, 3, Duration.ofMinutes(30), clock, signals, signals);
        SyncPause never = new SyncPause(signals, signals, new SyncFakes.Settings());
        SyncDispatcher dispatcher = SyncFakes.dispatcher(SyncFakes.engine(SyncFakes.registry(published, bzRemove,
                bitRemove), gigs, published, outbox, signals, clock, never, breakers), outbox, signals, clock, never,
                breakers);
        List<BandId> bands = IntStream.rangeClosed(1, 10).mapToObj(ManyBandsSyncTest::band).toList();
        for (BandId band : bands) {
            for (int g = 0; g < 4; g++) {
                Gig gig = TestGigs.gig("Gig " + g, "Klub " + g);
                queueDelete(band, BANDZONE, gig);
                queueDelete(band, BANDSINTOWN, gig);
            }
        }

        runWorkers(dispatcher);

        assertThat(outbox.all()).filteredOn(t -> !t.band().equals(brokenBand))
                .as("every other band's work is done").allSatisfy(t -> assertThat(t.status()).isEqualTo(SyncStatus.DONE));
        assertThat(outbox.all()).filteredOn(t -> t.band().equals(brokenBand) && t.platform().equals(BANDSINTOWN))
                .as("the broken band's other platform too").allSatisfy(t -> assertThat(t.status()).isEqualTo(SyncStatus.DONE));
        assertThat(outbox.all()).filteredOn(t -> t.band().equals(brokenBand) && t.platform().equals(BANDZONE))
                .as("its broken account's work waits").allSatisfy(t -> assertThat(t.status()).isEqualTo(SyncStatus.PENDING));
        assertThat(bzRemove.order).filteredOn(brokenBand::equals).as("held back after two failures").hasSize(2);
        assertThat(breakers.held(new Lane(brokenBand, BANDZONE))).isTrue();
        assertThat(breakers.held(new Lane(band(1), BANDZONE))).as("one band's broken account isn't the platform").isFalse();
        assertThat(breakers.breakers(band(1))).isEmpty();
        assertThat(breakers.breakers(brokenBand)).singleElement().satisfies(b -> assertThat(b.everyone()).isFalse());

        assertThat(bzRemove.mostInOneLane.get()).as("never two runs of one band on one platform at once").isEqualTo(1);
        assertThat(bitRemove.mostInOneLane.get()).isEqualTo(1);
        assertThat(Math.max(bzRemove.mostAtOnce.get(), bitRemove.mostAtOnce.get()))
                .as("never more than the workers").isLessThanOrEqualTo(WORKERS);
        assertThat(Math.max(bzRemove.mostAtOnce.get(), bitRemove.mostAtOnce.get())).as("but more than one").isGreaterThan(1);
    }

    @Test
    void many_bands_failing_on_one_platform_at_once_hold_it_back_for_everyone_until_fewer_do() {
        PlatformBreakers breakers = new PlatformBreakers(1, 3, Duration.ofMinutes(30), clock, signals, signals);
        for (int n = 1; n <= 2; n++) {
            breakers.failed(new Lane(band(n), BANDSINTOWN), "the portal answers 503");
        }
        assertThat(breakers.held(new Lane(band(9), BANDSINTOWN))).as("two bands failing: theirs only").isFalse();

        clock.advance(Duration.ofMinutes(10));
        breakers.failed(new Lane(band(3), BANDSINTOWN), "the portal answers 503");

        assertThat(breakers.held(new Lane(band(9), BANDSINTOWN))).as("three: the platform seems down").isTrue();
        assertThat(breakers.held(new Lane(band(9), BANDZONE))).isFalse();
        assertThat(breakers.breakers(band(9))).singleElement().satisfies(b -> {
            assertThat(b.everyone()).isTrue();
            assertThat(b.failures()).as("bands failing").isEqualTo(3);
            assertThat(b.heldUntil()).as("until the first of them is tried again")
                    .isEqualTo(clock.instant().minus(Duration.ofMinutes(10)).plus(Duration.ofMinutes(30)));
        });

        clock.advance(Duration.ofMinutes(21));
        assertThat(breakers.held(new Lane(band(9), BANDSINTOWN))).as("fewer held back: the others go on").isFalse();
        assertThat(breakers.held(new Lane(band(3), BANDSINTOWN))).as("the last one still waits").isTrue();
        breakers.succeeded(new Lane(band(3), BANDSINTOWN));
        assertThat(breakers.breakers(band(3))).filteredOn(b -> !b.everyone()).isEmpty();
    }

    @Test
    void the_bands_take_turns_and_a_lane_is_one_workers_at_a_time() {
        FairTurns turns = new FairTurns();
        BandId a = band(1);
        BandId b = band(2);
        BandId c = band(3);
        List<SyncTask> due = new ArrayList<>();
        for (BandId band : List.of(a, a, a, b, c)) {
            due.add(outbox.enqueue(band, TestGigs.gig("G" + due.size(), "K").id(), "g", BANDZONE, SyncAction.UPDATE,
                    clock.instant()));
        }
        due.add(outbox.enqueue(a, TestGigs.gig("Elsewhere", "K").id(), "g", BANDSINTOWN, SyncAction.UPDATE,
                clock.instant()));

        SyncTask first = turns.claim(due, t -> true).orElseThrow();
        SyncTask second = turns.claim(due, t -> true).orElseThrow();
        SyncTask third = turns.claim(due, t -> true).orElseThrow();
        SyncTask fourth = turns.claim(due, t -> true).orElseThrow();

        assertThat(List.of(first.band(), second.band(), third.band(), fourth.band())).containsExactly(a, b, c, a);
        assertThat(fourth.platform()).as("a's other lane — its first is still running").isEqualTo(BANDSINTOWN);
        assertThat(turns.claim(due, t -> true)).as("every lane with due work is running").isEmpty();
        turns.release(Lane.of(first));
        assertThat(turns.claim(due, t -> true)).get().satisfies(t -> assertThat(Lane.of(t)).isEqualTo(Lane.of(first)));
        assertThat(turns.claim(due, t -> !t.band().equals(a))).isEmpty();
    }
}
