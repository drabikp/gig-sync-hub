package sk.drabikp.gigsynchub.sync.application.engine;

import org.jmolecules.architecture.hexagonal.Application;
import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.gig.domain.platform.Platform;
import sk.drabikp.gigsynchub.sync.application.port.in.PlatformBreakerUseCase;
import sk.drabikp.gigsynchub.sync.application.port.out.SyncNotifier;
import sk.drabikp.gigsynchub.sync.application.port.out.SyncTrigger;
import sk.drabikp.gigsynchub.sync.domain.Lane;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Circuit breakers per lane — a band's account on a platform. A batch whose every result is a (temporary)
 * failure counts against the lane; any other result — done, refused, failed for good — shows the platform
 * answering the band and closes it. After {@code threshold} failed batches in a row the lane is held back
 * for {@code cooldown}: its work stays queued, the band's other platforms and every other band go on. Then
 * one batch is let through as a trial: success closes the breaker, a failure holds the lane back again.
 * When {@code platformThreshold} bands are held back on one platform at once, the platform seems down: it
 * is held back for every band until fewer are. In memory — a restart starts closed.
 */
@Application
public class PlatformBreakers implements PlatformBreakerUseCase, PlatformHealth {

    private final int threshold;
    private final int platformThreshold;
    private final Duration cooldown;
    private final Clock clock;
    private final SyncNotifier notifier;
    private final SyncTrigger trigger;
    private final Map<Lane, Breaker> breakers = new HashMap<>();

    public PlatformBreakers(int threshold, int platformThreshold, Duration cooldown, Clock clock,
                            SyncNotifier notifier, SyncTrigger trigger) {
        if (threshold < 1 || platformThreshold < 1) {
            throw new IllegalArgumentException("thresholds must be at least 1");
        }
        this.threshold = threshold;
        this.platformThreshold = platformThreshold;
        this.cooldown = cooldown;
        this.clock = clock;
        this.notifier = notifier;
        this.trigger = trigger;
    }

    /** Breakers that never hold anything back (tests, or switched off). */
    public static PlatformBreakers never(Clock clock) {
        return new PlatformBreakers(Integer.MAX_VALUE, Integer.MAX_VALUE, Duration.ZERO, clock, SyncNotifier.NONE,
                () -> { });
    }

    @Override
    public synchronized boolean held(Lane lane) {
        Instant now = clock.instant();
        Breaker own = breakers.get(lane);
        return own != null && own.held(now) || everyone(lane.platform(), now).isPresent();
    }

    @Override
    public void succeeded(Lane lane) {
        boolean wasHeldBack;
        boolean platformWas;
        boolean platformIs;
        synchronized (this) {
            Instant now = clock.instant();
            platformWas = everyone(lane.platform(), now).isPresent();
            Breaker breaker = breakers.remove(lane);
            wasHeldBack = breaker != null && breaker.heldUntil() != null;
            platformIs = everyone(lane.platform(), now).isPresent();
        }
        tell(lane, wasHeldBack, platformWas != platformIs);
    }

    @Override
    public void failed(Lane lane, String failure) {
        Breaker now;
        boolean platformWas;
        boolean platformIs;
        synchronized (this) {
            Instant at = clock.instant();
            platformWas = everyone(lane.platform(), at).isPresent();
            Breaker before = breakers.get(lane);
            int failures = before == null ? 1 : before.failures() + 1;
            boolean trialFailed = before != null && before.heldUntil() != null;
            Instant heldUntil = trialFailed || failures >= threshold ? at.plus(cooldown) : null;
            now = new Breaker(lane.platform(), failures, heldUntil, false, failure, false);
            breakers.put(lane, now);
            platformIs = everyone(lane.platform(), at).isPresent();
        }
        tell(lane, now.heldUntil() != null, platformWas != platformIs);
    }

    @Override
    public synchronized List<Breaker> breakers(BandId band) {
        Instant now = clock.instant();
        List<Breaker> shown = new ArrayList<>();
        breakers.forEach((lane, b) -> {
            if (lane.band().equals(band)) {
                shown.add(b.heldUntil() != null && !b.held(now)
                        ? new Breaker(b.platform(), b.failures(), b.heldUntil(), true, b.lastFailure(), false) : b);
            }
        });
        breakers.keySet().stream().map(Lane::platform).distinct().sorted()
                .flatMap(platform -> everyone(platform, now).stream())
                .forEach(shown::add);
        return shown;
    }

    @Override
    public void resume(BandId band, Platform platform) {
        synchronized (this) {
            breakers.remove(new Lane(band, platform));
        }
        notifier.changed(band);
        trigger.wake();
    }

    /**
     * The platform held back for everyone: while {@code platformThreshold} bands are held back on it — until
     * fewer are, i.e. the {@code platformThreshold}-th latest of their hold-backs ends.
     */
    private Optional<Breaker> everyone(Platform platform, Instant now) {
        List<Breaker> held = breakers.entrySet().stream()
                .filter(e -> e.getKey().platform().equals(platform) && e.getValue().held(now))
                .map(Map.Entry::getValue)
                .sorted(Comparator.comparing(Breaker::heldUntil).reversed())
                .toList();
        if (held.size() < platformThreshold) {
            return Optional.empty();
        }
        return Optional.of(new Breaker(platform, held.size(), held.get(platformThreshold - 1).heldUntil(), false,
                held.getFirst().lastFailure(), true));
    }

    private void tell(Lane lane, boolean bandsChanged, boolean everyonesChanged) {
        if (everyonesChanged) {
            notifier.changedEverywhere();
        } else if (bandsChanged) {
            notifier.changed(lane.band());
        }
    }
}
