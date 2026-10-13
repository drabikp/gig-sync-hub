package sk.drabikp.gigsynchub.live;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import sk.drabikp.gigsynchub.gig.application.port.out.LiveUpdates;
import sk.drabikp.gigsynchub.gig.domain.BandId;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
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
 * {@value #MAX_PER_BAND} streams per band and {@value #MAX_STREAMS} on the server.
 *
 * <p><b>Nobody writes on the timer thread.</b> {@code emitter.send} is a blocking write to a socket,
 * so one page on a stalled connection — a phone that went into a tunnel, its window full and not yet
 * timed out — used to hold the one thread that fans out every OTHER band's events and sends every
 * heartbeat. Live updates stopped for everyone, and the idle streams other proxies then closed made
 * it look like a network fault. So each stream has its own small outbox and its own thread draining
 * it: a stalled page delays nothing but itself.
 *
 * <p>An event that does not fit a stream's outbox is DROPPED, and that is sound rather than a
 * compromise: an event carries no data, only the name of what changed, and the page answers it by
 * asking for that part again. A page too far behind to take the name will be told by the next one it
 * does take, or will ask for everything when it reconnects.
 */
@Component
class SseLiveUpdates implements LiveUpdates, InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(SseLiveUpdates.class);
    static final long COALESCE_MS = 300;
    static final long HEARTBEAT_S = 25;
    static final long LIFETIME_MIN = 30;
    static final int MAX_PER_BAND = 100;
    static final int MAX_STREAMS = 2000;
    /** A page this far behind is not reading; one more name would tell it nothing. */
    static final int OUTBOX = 8;

    /** A page's stream, the band it shows, and what is waiting to be written to it. */
    private record Stream(BandId band, SseEmitter emitter, BlockingQueue<SseEmitter.SseEventBuilder> outbox) {

        Stream(BandId band, SseEmitter emitter) {
            this(band, emitter, new ArrayBlockingQueue<>(OUTBOX));
        }

        /** Never blocks and never throws: whoever announces a change is not the one who waits for a page. */
        void offer(SseEmitter.SseEventBuilder event) {
            if (!outbox.offer(event)) {
                log.debug("A page is not keeping up; it is not told this time");
            }
        }
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
    /**
     * One thread per stream, parked on its own socket. Virtual, because a platform thread each would
     * be 2000 of them: here a stalled page costs a few kilobytes and nobody else's latency.
     */
    private final ExecutorService writers = Executors.newVirtualThreadPerTaskExecutor();

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
        if (bands.size() > MAX_PER_BAND || streams.size() > MAX_STREAMS) {
            // The oldest of THIS band's, under either cap. Ending the oldest on the server would
            // make one band's hundred tabs close another band's pages — a tenant able to blind the
            // others, for free, by opening windows.
            end(bands.getFirst());
        }
        emitter.onCompletion(() -> streams.remove(stream));
        emitter.onTimeout(() -> streams.remove(stream));
        emitter.onError(e -> streams.remove(stream));
        writers.execute(() -> drain(stream));
        stream.offer(SseEmitter.event().name("hello").data("live"));
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
                    .forEach(stream -> stream.offer(SseEmitter.event().name(name).data(name)));
        }, COALESCE_MS, TimeUnit.MILLISECONDS);
    }

    int streams() {
        return streams.size();
    }

    /** Ends a stream to make room (the oldest of its band). */
    private void end(Stream stream) {
        streams.remove(stream);
        try {
            stream.emitter().complete();
        } catch (RuntimeException e) {
            log.debug("Ending a live stream failed", e);
        }
    }

    private void heartbeat() {
        streams.forEach(stream -> stream.offer(SseEmitter.event().comment("still here")));
    }

    /**
     * One stream's writes, in order, until the page goes away. The take blocks on an empty outbox
     * and the send blocks on a full socket — both on this stream's own thread, which is the whole
     * point of having one.
     */
    private void drain(Stream stream) {
        try {
            while (streams.contains(stream)) {
                SseEmitter.SseEventBuilder event = stream.outbox().poll(HEARTBEAT_S, TimeUnit.SECONDS);
                if (event != null) {
                    stream.emitter().send(event);
                }
            }
        } catch (IOException | IllegalStateException e) {
            log.debug("A page's live stream is gone", e);
            streams.remove(stream);
            stream.emitter().completeWithError(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();                 // the server is stopping
        }
    }

    @Override
    public void destroy() {
        timer.shutdownNow();
        writers.shutdownNow();
        streams.forEach(stream -> stream.emitter().complete());
    }
}
