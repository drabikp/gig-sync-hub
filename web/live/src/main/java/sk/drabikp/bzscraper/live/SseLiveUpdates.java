package sk.drabikp.bzscraper.live;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import sk.drabikp.bzscraper.gig.application.port.out.LiveUpdates;
import sk.drabikp.bzscraper.gig.domain.BandId;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * {@link LiveUpdates} as server-sent events: every open page holds one stream for the band it shows
 * ({@link #open(BandId)}); a band's change is sent to that band's streams, a change everyone's pages
 * show to all of them, as an event named after its topic. Changes come in bursts (a sync batch, an
 * import), so a topic is sent once per {@value #COALESCE_MS} ms at most (per band); a comment every
 * {@value #HEARTBEAT_S} s keeps idle streams open through proxies. A stream ends after {@value #LIFETIME_MIN}
 * minutes (the page opens a new one by itself — so a member removed from the band stops hearing it); at most
 * {@value #MAX_PER_BAND} streams per band and {@value #MAX_STREAMS} on the server — over that, the oldest ends.
 */
@Component
class SseLiveUpdates implements LiveUpdates, InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(SseLiveUpdates.class);
    static final long COALESCE_MS = 300;
    static final long HEARTBEAT_S = 25;
    static final long LIFETIME_MIN = 30;
    static final int MAX_PER_BAND = 100;
    static final int MAX_STREAMS = 2000;

    /** A page's stream and the band it shows. */
    private record Stream(BandId band, SseEmitter emitter) {
    }

    /** A change waiting to be sent: whose ({@code null} = everyone's) and what. */
    private record Due(BandId band, Topic topic) {

        boolean reaches(Stream stream) {
            return band == null || band.equals(stream.band());
        }
    }

    private final List<Stream> streams = new CopyOnWriteArrayList<>();
    private final Set<Due> due = new HashSet<>();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "live-updates");
        thread.setDaemon(true);
        return thread;
    });

    /** Starts the heartbeat once the bean is built (not in the constructor: {@code this} is complete by then). */
    @Override
    public void afterPropertiesSet() {
        timer.scheduleAtFixedRate(this::heartbeat, HEARTBEAT_S, HEARTBEAT_S, TimeUnit.SECONDS);
    }

    /** A new stream for a page showing the band; it ends when the page goes away (or the server stops). */
    SseEmitter open(BandId band) {
        return open(band, new SseEmitter(TimeUnit.MINUTES.toMillis(LIFETIME_MIN)));
    }

    SseEmitter open(BandId band, SseEmitter emitter) {
        Stream stream = new Stream(band, emitter);
        streams.add(stream);
        List<Stream> bands = streams.stream().filter(s -> s.band().equals(band)).toList();
        if (bands.size() > MAX_PER_BAND) {
            end(bands.getFirst());
        }
        if (streams.size() > MAX_STREAMS) {
            end(streams.getFirst());
        }
        emitter.onCompletion(() -> streams.remove(stream));
        emitter.onTimeout(() -> streams.remove(stream));
        emitter.onError(e -> streams.remove(stream));
        send(stream, SseEmitter.event().name("hello").data("live"));
        return emitter;
    }

    @Override
    public void changed(BandId band, Topic topic) {
        schedule(new Due(band, topic));
    }

    @Override
    public void changedEverywhere(Topic topic) {
        schedule(new Due(null, topic));
    }

    private void schedule(Due change) {
        synchronized (due) {
            if (!due.add(change)) {
                return;
            }
        }
        timer.schedule(() -> {
            synchronized (due) {
                due.remove(change);
            }
            String name = change.topic().name().toLowerCase(Locale.ROOT);
            streams.stream().filter(change::reaches)
                    .forEach(stream -> send(stream, SseEmitter.event().name(name).data(name)));
        }, COALESCE_MS, TimeUnit.MILLISECONDS);
    }

    int streams() {
        return streams.size();
    }

    /** Ends a stream to make room (the oldest). */
    private void end(Stream stream) {
        streams.remove(stream);
        try {
            stream.emitter().complete();
        } catch (RuntimeException e) {
            log.debug("Ending a live stream failed", e);
        }
    }

    private void heartbeat() {
        streams.forEach(stream -> send(stream, SseEmitter.event().comment("still here")));
    }

    private void send(Stream stream, SseEmitter.SseEventBuilder event) {
        try {
            stream.emitter().send(event);
        } catch (IOException | IllegalStateException e) {
            log.debug("A page's live stream is gone", e);
            streams.remove(stream);
            stream.emitter().completeWithError(e);
        }
    }

    @Override
    public void destroy() {
        timer.shutdownNow();
        streams.forEach(stream -> stream.emitter().complete());
    }
}
