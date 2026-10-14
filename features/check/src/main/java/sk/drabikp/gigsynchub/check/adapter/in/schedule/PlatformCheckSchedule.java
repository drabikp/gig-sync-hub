package sk.drabikp.gigsynchub.check.adapter.in.schedule;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import sk.drabikp.gigsynchub.accounts.application.port.in.BandDirectory;
import sk.drabikp.gigsynchub.check.application.port.in.CheckPlatformsUseCase;
import sk.drabikp.gigsynchub.check.config.CheckProperties;
import sk.drabikp.gigsynchub.gig.domain.BandId;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Reads the platforms and compares them with the catalog once a night for every band, one after
 * another ({@code gigsynchub.check.cron}, default 04:30), so drift is found without anyone looking —
 * read-only. On its own thread, like the sync worker; off with
 * {@code gigsynchub.check.enabled=false} (tests).
 *
 * <p><b>A night is a budget, and the bands take turns.</b> One band's check reads every platform it
 * is on, over the network and paced like a person, so the night's length is the bands' number times
 * that — and the loop used to run until it had been through all of them, however long that took,
 * with the next run scheduled only afterwards. Two things went wrong as the bands multiplied: the
 * cadence drifted (the cron's next time was worked out from when the LAST run finished), and a run
 * that overran was still reading platforms in the middle of the day, against accounts whose owners
 * were using them.
 *
 * <p>So the next run is scheduled FIRST, from the cron and nothing else, and the walk stops when
 * {@link CheckProperties#budget()} is spent — carrying on tomorrow at the band it stopped at, so a
 * budget that always runs out still gets round every band instead of checking the first few for
 * ever. A band whose own check fails is still only that band's loss.
 */
@Component
class PlatformCheckSchedule {

    private static final Logger log = LoggerFactory.getLogger(PlatformCheckSchedule.class);

    private final CheckPlatformsUseCase platformCheck;
    private final BandDirectory bands;
    private final boolean enabled;
    private final Clock clock;
    private final CronExpression cron;
    private final Duration budget;

    /** Where tomorrow's round starts: the band this one did not reach. */
    private int from;
    private final ScheduledExecutorService thread = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "platform-check");
        t.setDaemon(true);
        return t;
    });

    public PlatformCheckSchedule(CheckPlatformsUseCase platformCheck, BandDirectory bands, CheckProperties properties,
                                 Clock clock) {
        this.platformCheck = platformCheck;
        this.bands = bands;
        this.enabled = properties.enabled();
        this.cron = CronExpression.parse(properties.cron());
        this.budget = properties.budget();
        this.clock = clock;
    }

    @PostConstruct
    void start() {
        if (enabled) {
            scheduleNext();
        }
    }

    @PreDestroy
    void stop() {
        thread.shutdownNow();
    }

    private void scheduleNext() {
        ZonedDateTime now = ZonedDateTime.now(clock);
        ZonedDateTime next = cron.next(now);
        if (next != null) {
            thread.schedule(this::run, Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private void run() {
        scheduleNext();                                   // the cadence is the cron's, not this round's length
        try {
            round(bands.all(), clock.instant().plus(budget));
        } catch (RuntimeException e) {
            log.warn("The nightly platform check failed", e);
        }
    }

    /**
     * One night's walk, from where the last one stopped and for as long as {@code until} allows.
     * Package-private because this is the part worth a test: the executor and the cron are not.
     */
    void round(List<BandId> all, Instant until) {
        if (all.isEmpty()) {
            from = 0;
            return;
        }
        int start = Math.floorMod(from, all.size());
        for (int done = 0; done < all.size(); done++) {
            int at = (start + done) % all.size();
            if (!clock.instant().isBefore(until)) {
                from = at;
                log.warn("The nightly platform check spent its {} after {} of {} bands; the rest go first tomorrow",
                        budget, done, all.size());
                return;
            }
            check(all.get(at));
        }
        from = start;
    }

    /** One band's check; its failure doesn't keep the other bands from theirs. */
    private void check(BandId band) {
        try {
            platformCheck.check(band).ifPresent(check -> log.info(
                    "Platform check of band {}: {} difference(s), unreadable: {}", band, check.drifts().size(),
                    check.unreadable().keySet()));
        } catch (RuntimeException e) {
            log.warn("The nightly platform check of band {} failed", band, e);
        }
    }
}
