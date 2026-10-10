package sk.drabikp.bzscraper.sync.application.port.in;

import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;

import java.time.Instant;
import java.util.List;

/**
 * The sync's circuit breakers: after several failures in a row of a band's work on a platform (its
 * account broke, the portal changed, it is down), that band's work there is held back for a while
 * instead of failing again and again — the band's other platforms and every other band go on. When many
 * bands fail on one platform at once, the platform itself seems down: it is held back for everyone.
 */
public interface PlatformBreakerUseCase {

    /** The band's breakers that aren't plainly closed, and the platforms held back for everyone. */
    List<Breaker> breakers(BandId band);

    /** Lets the band's work on the platform run again now (the user fixed the cause). */
    void resume(BandId band, Platform platform);

    /**
     * {@code failures} in a row; {@code heldUntil} non-null while the work is held back — once it has
     * passed, the next batch is a trial ({@code trial}): success lets the work flow again, a failure holds
     * it back again. {@code everyone}: the platform is held back for every band (many bands failing at
     * once), {@code failures} then counts those bands.
     */
    record Breaker(Platform platform, int failures, Instant heldUntil, boolean trial, String lastFailure,
                   boolean everyone) {

        public boolean held(Instant now) {
            return heldUntil != null && now.isBefore(heldUntil);
        }
    }
}
