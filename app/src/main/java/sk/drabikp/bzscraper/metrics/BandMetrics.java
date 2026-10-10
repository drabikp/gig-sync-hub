package sk.drabikp.bzscraper.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.accounts.application.port.in.BandDirectory;
import sk.drabikp.bzscraper.browser.BrowserPool;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.sync.application.port.in.PlatformBreakerUseCase;
import sk.drabikp.bzscraper.sync.application.port.in.SyncLogUseCase;
import sk.drabikp.bzscraper.sync.domain.SyncCounts;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * What the operator watches (the management port, {@code /actuator/metrics}, this machine only — never
 * the bands): per band its sync work ({@code bzscraper.sync.tasks}, by status) and its platforms held back
 * ({@code bzscraper.sync.held}); the server's browsers ({@code bzscraper.browser.open} of
 * {@code bzscraper.browser.max}) and each platform's sessions ({@code bzscraper.browser.sessions}).
 * The per-band numbers are read again every minute.
 */
@Component
class BandMetrics implements MeterBinder {

    private static final Logger log = LoggerFactory.getLogger(BandMetrics.class);
    private static final long REFRESH_SECONDS = 60;

    private final BandDirectory bands;
    private final SyncLogUseCase syncLog;
    private final PlatformBreakerUseCase breakers;
    private final BrowserPool browsers;
    private final ScheduledExecutorService refresh = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "band-metrics");
        thread.setDaemon(true);
        return thread;
    });

    BandMetrics(BandDirectory bands, SyncLogUseCase syncLog, PlatformBreakerUseCase breakers, BrowserPool browsers) {
        this.bands = bands;
        this.syncLog = syncLog;
        this.breakers = breakers;
        this.browsers = browsers;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("bzscraper.browser.open", browsers, BrowserPool::open)
                .description("Browsers open on this server, warm ones included").register(registry);
        Gauge.builder("bzscraper.browser.max", browsers, BrowserPool::max)
                .description("Browsers this server opens at most").register(registry);
        MultiGauge sessions = MultiGauge.builder("bzscraper.browser.sessions")
                .description("Platform sessions running").register(registry);
        MultiGauge tasks = MultiGauge.builder("bzscraper.sync.tasks")
                .description("A band's platform work by status").register(registry);
        MultiGauge held = MultiGauge.builder("bzscraper.sync.held")
                .description("A band's platforms held back by a circuit breaker").register(registry);
        Runnable read = () -> {
            try {
                sessions.register(browsers.sessionsInUse().entrySet().stream()
                        .map(e -> MultiGauge.Row.of(Tags.of("platform", e.getKey()), e.getValue())).toList(), true);
                List<MultiGauge.Row<?>> taskRows = new ArrayList<>();
                List<MultiGauge.Row<?>> heldRows = new ArrayList<>();
                Instant now = Instant.now();
                for (BandId band : bands.all()) {
                    SyncCounts counts = syncLog.counts(band);
                    Tags tags = Tags.of("band", band.toString());
                    taskRows.add(MultiGauge.Row.of(tags.and("status", "queued"), counts.queued()));
                    taskRows.add(MultiGauge.Row.of(tags.and("status", "running"), counts.running()));
                    taskRows.add(MultiGauge.Row.of(tags.and("status", "retrying"), counts.retrying()));
                    taskRows.add(MultiGauge.Row.of(tags.and("status", "failed"), counts.failed()));
                    heldRows.add(MultiGauge.Row.of(tags,
                            breakers.breakers(band).stream().filter(b -> b.held(now)).count()));
                }
                tasks.register(taskRows, true);
                held.register(heldRows, true);
            } catch (RuntimeException e) {
                log.warn("Reading the bands' metrics failed: {}", e.getMessage());
            }
        };
        refresh.scheduleWithFixedDelay(read, 0, REFRESH_SECONDS, TimeUnit.SECONDS);
    }

    @PreDestroy
    void stop() {
        refresh.shutdownNow();
    }
}
