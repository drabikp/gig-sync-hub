package sk.drabikp.gigsynchub.live;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import sk.drabikp.gigsynchub.gig.application.port.out.LiveUpdates;
import sk.drabikp.gigsynchub.gig.domain.BandId;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static sk.drabikp.gigsynchub.live.SseLiveUpdates.COALESCE_MS;

class SseLiveUpdatesTest {

    private static final BandId OTHER = new BandId(UUID.fromString("00000000-0000-0000-0000-000000000002"));

    /** A page's stream that keeps the names of the events sent to it. */
    private static final class Page extends SseEmitter {

        private final List<String> events = new CopyOnWriteArrayList<>();

        @Override
        public void send(SseEventBuilder event) {
            String text = event.build().stream().map(part -> String.valueOf(part.getData()))
                    .collect(Collectors.joining());
            text.lines().filter(line -> line.startsWith("event:")).forEach(line -> events.add(line.substring(6)));
        }
    }

    @Test
    void a_stream_is_kept_while_open_and_a_change_with_no_page_open_goes_nowhere() throws Exception {
        SseLiveUpdates live = new SseLiveUpdates();
        try {
            live.changed(BandId.FIRST, LiveUpdates.Topic.SYNC);
            live.open(BandId.FIRST);
            assertThat(live.streams()).isEqualTo(1);
            Thread.sleep(SseLiveUpdates.COALESCE_MS * 2);
        } finally {
            live.destroy();
        }
    }

    @Test
    void a_bands_change_reaches_only_its_pages_and_a_change_for_everyone_all_of_them() throws Exception {
        SseLiveUpdates live = new SseLiveUpdates();
        Page first = new Page();
        Page other = new Page();
        try {
            live.open(BandId.FIRST, first);
            live.open(OTHER, other);

            live.changed(BandId.FIRST, LiveUpdates.Topic.GIGS);
            live.changed(BandId.FIRST, LiveUpdates.Topic.GIGS);           // a burst: sent once
            live.changedEverywhere(LiveUpdates.Topic.SYNC);
            Thread.sleep(SseLiveUpdates.COALESCE_MS * 3);

            assertThat(first.events).containsExactlyInAnyOrder("hello", "gigs", "sync");
            assertThat(other.events).containsExactlyInAnyOrder("hello", "sync");
        } finally {
            live.destroy();
        }
    }

    /**
     * The one that matters: a page whose socket has stopped taking bytes — a phone in a tunnel, its
     * window full and not yet timed out — must not stop anyone else being told. It used to: every
     * fan-out and every heartbeat went out on one thread, so one stalled write held all of them, and
     * the idle streams other proxies then dropped made it look like a network fault. Put the writes
     * back on the timer thread and this test does not finish.
     */
    @Test
    void a_page_that_has_stalled_delays_nothing_but_itself() throws Exception {
        SseLiveUpdates live = new SseLiveUpdates();
        CountDownLatch inTheWrite = new CountDownLatch(1);
        CountDownLatch letItGo = new CountDownLatch(1);
        Page reading = new Page();
        SseEmitter stalled = new SseEmitter() {
            private boolean greeted;

            @Override
            public void send(SseEventBuilder event) {
                if (!greeted) {
                    greeted = true;                             // the hello goes through; nothing after it does
                    return;
                }
                inTheWrite.countDown();
                try {
                    letItGo.await(20, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        try {
            live.open(BandId.FIRST, stalled);
            live.open(OTHER, reading);

            live.changedEverywhere(LiveUpdates.Topic.SYNC);
            assertThat(inTheWrite.await(20, TimeUnit.SECONDS))
                    .as("the stalled page is inside its write and staying there").isTrue();

            live.changedEverywhere(LiveUpdates.Topic.GIGS);
            assertThat(heard(reading, "gigs")).as("the reading page is told while the other one hangs").isTrue();
        } finally {
            letItGo.countDown();
            live.destroy();
        }
    }

    private boolean heard(Page page, String event) throws InterruptedException {
        for (int tries = 0; tries < 100; tries++) {
            if (page.events.contains(event)) {
                return true;
            }
            Thread.sleep(COALESCE_MS);
        }
        return false;
    }
}
