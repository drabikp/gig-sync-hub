package sk.drabikp.bzscraper.sync.adapter.in.worker;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.sync.application.port.in.DispatchSyncUseCase;
import sk.drabikp.bzscraper.sync.application.port.in.SyncWorkSignal;
import sk.drabikp.bzscraper.sync.config.SyncProperties;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs the sync outbox in the background on ONE thread — so platform work never
 * overlaps — until nothing is due. Woken when tasks are queued, and checks every
 * {@code bzscraper.sync.poll-seconds} anyway (retries come due by time). On start it first
 * settles tasks a previous run left RUNNING. Off with {@code bzscraper.sync.worker.enabled=false}
 * (tests): tasks are then queued but never run.
 */
@Component
class SyncWorker {

    private static final Logger log = LoggerFactory.getLogger(SyncWorker.class);

    private final DispatchSyncUseCase dispatcher;
    private final SyncWorkSignal workSignal;
    private final boolean enabled;
    private final long pollSeconds;
    private final ScheduledExecutorService thread = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sync-worker");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean wakeQueued = new AtomicBoolean();

    public SyncWorker(DispatchSyncUseCase dispatcher, SyncWorkSignal workSignal, SyncProperties properties) {
        this.dispatcher = dispatcher;
        this.workSignal = workSignal;
        this.enabled = properties.worker().enabled();
        this.pollSeconds = properties.pollSeconds();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!enabled) {
            log.info("Sync worker is off (bzscraper.sync.worker.enabled=false): platform tasks stay queued");
            return;
        }
        thread.execute(this::recover);
        thread.scheduleWithFixedDelay(this::drain, pollSeconds, pollSeconds, TimeUnit.SECONDS);
        workSignal.onWork(this::wake);                  // work queued before now is drained by recover
    }

    /** New work was queued: drain now instead of at the next poll. */
    public void wake() {
        if (enabled && wakeQueued.compareAndSet(false, true)) {
            thread.execute(() -> {
                wakeQueued.set(false);
                drain();
            });
        }
    }

    @PreDestroy
    public void stop() {
        thread.shutdownNow();
    }

    private void recover() {
        try {
            dispatcher.recoverInterrupted();
        } catch (RuntimeException e) {
            log.error("Settling interrupted sync tasks failed", e);
        }
        drain();
    }

    private void drain() {
        try {
            while (!Thread.currentThread().isInterrupted() && dispatcher.runNext()) {
                // one task (or publish batch) per round
            }
        } catch (RuntimeException e) {
            log.error("Sync worker round failed; next round in {} s", pollSeconds, e);
        }
    }
}
