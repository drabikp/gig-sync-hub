package sk.drabikp.bzscraper.live;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import sk.drabikp.bzscraper.gig.application.port.out.LiveUpdates;
import sk.drabikp.bzscraper.gig.domain.BandId;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

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
}
