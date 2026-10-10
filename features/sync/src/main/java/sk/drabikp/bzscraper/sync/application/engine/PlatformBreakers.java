package sk.drabikp.bzscraper.sync.application.engine;

import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.sync.application.port.in.PlatformBreakerUseCase;
import sk.drabikp.bzscraper.sync.application.port.out.SyncNotifier;
import sk.drabikp.bzscraper.sync.application.port.out.SyncTrigger;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Circuit breaker per platform. A batch whose every result is a (temporary) failure counts
 * against the platform; any other result — done, refused, failed for good — shows the
 * platform answering and closes it. After {@code threshold} failed batches in a row the
 * platform is held back for {@code cooldown}: its work stays queued, other platforms go on.
 * Then one batch is let through as a trial: success closes the breaker, a failure holds the
 * platform back again. In memory — a restart starts closed.
 */
public class PlatformBreakers implements PlatformBreakerUseCase, PlatformHealth {

    private final int threshold;
    private final Duration cooldown;
    private final Clock clock;
    private final SyncNotifier notifier;
    private final SyncTrigger trigger;
    private final Map<Platform, Breaker> breakers = new HashMap<>();

    public PlatformBreakers(int threshold, Duration cooldown, Clock clock, SyncNotifier notifier, SyncTrigger trigger) {
        if (threshold < 1) {
            throw new IllegalArgumentException("threshold must be at least 1");
        }
        this.threshold = threshold;
        this.cooldown = cooldown;
        this.clock = clock;
        this.notifier = notifier;
        this.trigger = trigger;
    }

    /** Breakers that never hold a platform back (tests, or switched off). */
    public static PlatformBreakers never(Clock clock) {
        return new PlatformBreakers(Integer.MAX_VALUE, Duration.ZERO, clock, SyncNotifier.NONE, () -> { });
    }

    @Override
    public synchronized Set<Platform> held() {
        Instant now = clock.instant();
        Set<Platform> held = new TreeSet<>();
        breakers.values().stream().filter(b -> b.held(now)).forEach(b -> held.add(b.platform()));
        return held;
    }

    @Override
    public synchronized boolean held(Platform platform) {
        Breaker breaker = breakers.get(platform);
        return breaker != null && breaker.held(clock.instant());
    }

    @Override
    public void succeeded(Platform platform) {
        boolean wasHeldBack;
        synchronized (this) {
            Breaker breaker = breakers.remove(platform);
            wasHeldBack = breaker != null && breaker.heldUntil() != null;
        }
        if (wasHeldBack) {
            notifier.changedEverywhere();
        }
    }

    @Override
    public void failed(Platform platform, String failure) {
        Breaker now;
        synchronized (this) {
            Breaker before = breakers.get(platform);
            int failures = before == null ? 1 : before.failures() + 1;
            boolean trialFailed = before != null && before.heldUntil() != null;
            Instant heldUntil = trialFailed || failures >= threshold ? clock.instant().plus(cooldown) : null;
            now = new Breaker(platform, failures, heldUntil, false, failure);
            breakers.put(platform, now);
        }
        if (now.heldUntil() != null) {
            notifier.changedEverywhere();
        }
    }

    @Override
    public synchronized List<Breaker> breakers() {
        Instant now = clock.instant();
        return breakers.values().stream()
                .map(b -> b.heldUntil() != null && !b.held(now)
                        ? new Breaker(b.platform(), b.failures(), b.heldUntil(), true, b.lastFailure()) : b)
                .toList();
    }

    @Override
    public void resume(Platform platform) {
        synchronized (this) {
            breakers.remove(platform);
        }
        notifier.changedEverywhere();
        trigger.wake();
    }
}
