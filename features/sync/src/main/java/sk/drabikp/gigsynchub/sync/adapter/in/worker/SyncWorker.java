package sk.drabikp.gigsynchub.sync.adapter.in.worker;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import sk.drabikp.gigsynchub.sync.application.port.in.DispatchSyncUseCase;
import sk.drabikp.gigsynchub.sync.application.port.in.SyncWorkSignal;
import sk.drabikp.gigsynchub.sync.config.SyncProperties;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs the sync outbox in the background on {@code gigsynchub.sync.worker.threads} threads (2): each takes
 * the next workload — a band's work of one action on one platform, taken through its workflow by the
 * engine; the bands in turn, one workload per lane (a band's account on a platform) at a time, so one
 * band's slow or broken platform doesn't hold up the others — until nothing is due. This loop is how work
 * is HANDED OUT, not how a workflow is driven: the engine runs a workflow to its end (or to the end of
 * what a pass allows) before this thread asks for anything else. Woken when tasks
 * are queued, and checks every {@code gigsynchub.sync.poll-seconds} anyway (retries come due by time). On
 * start it first settles tasks a previous run left RUNNING. Off with {@code gigsynchub.sync.worker.enabled=false}
 * (tests): tasks are then queued but never run.
 */
@Component
class SyncWorker {

    private static final Logger log = LoggerFactory.getLogger(SyncWorker.class);

    private final DispatchSyncUseCase dispatcher;
    private final SyncWorkSignal workSignal;
    private final boolean enabled;
    private final int threads;
    private final long pollSeconds;
    private final ExecutorService workers;
    private final ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sync-ticker");
        t.setDaemon(true);
        return t;
    });
    /** Workers draining right now (at most {@link #threads}). */
    private final AtomicInteger draining = new AtomicInteger();

    public SyncWorker(DispatchSyncUseCase dispatcher, SyncWorkSignal workSignal, SyncProperties properties) {
        this.dispatcher = dispatcher;
        this.workSignal = workSignal;
        this.enabled = properties.worker().enabled();
        this.threads = properties.worker().threads();
        this.pollSeconds = properties.pollSeconds();
        AtomicInteger number = new AtomicInteger();
        this.workers = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "sync-worker-" + number.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!enabled) {
            log.info("Sync worker is off (gigsynchub.sync.worker.enabled=false): platform tasks stay queued");
            return;
        }
        draining.incrementAndGet();
        workers.execute(() -> {
            try {
                dispatcher.recoverInterrupted();
            } catch (RuntimeException e) {
                log.error("Settling interrupted sync tasks failed", e);
            } finally {
                draining.decrementAndGet();
            }
            wake();
            ticker.scheduleWithFixedDelay(this::wake, pollSeconds, pollSeconds, TimeUnit.SECONDS);
            workSignal.onWork(this::wake);              // work queued before now is drained by this first wake
        });
    }

    /** New work was queued (or time passed): every idle worker drains. */
    public void wake() {
        if (!enabled) {
            return;
        }
        for (int busy = draining.get(); busy < threads; busy = draining.get()) {
            if (draining.compareAndSet(busy, busy + 1)) {
                workers.execute(this::drain);
            }
        }
    }

    @PreDestroy
    public void stop() {
        ticker.shutdownNow();
        workers.shutdownNow();
    }

    private void drain() {
        try {
            while (!Thread.currentThread().isInterrupted() && dispatcher.runNext()) {
                // one workload per round
            }
        } catch (RuntimeException e) {
            log.error("Sync worker round failed; next round in {} s", pollSeconds, e);
        } finally {
            draining.decrementAndGet();
        }
    }
}
