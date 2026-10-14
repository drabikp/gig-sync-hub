package sk.drabikp.gigsynchub.housekeeping;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import sk.drabikp.gigsynchub.accounts.application.port.in.ForgetOldRecordsUseCase;
import sk.drabikp.gigsynchub.calendar.application.port.in.ForgetOldCalendarCopyUseCase;
import sk.drabikp.gigsynchub.sync.application.port.in.ForgetOldSyncWorkUseCase;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * What the app forgets, nightly. Four tables used to grow without bound and without a plan: one
 * {@code sync_task} row per attempt per gig per platform with its handful of {@code sync_log} lines,
 * one {@code audit_event} per change any member makes, one {@code calendar_event} per event a band's
 * calendar ever held, and every invitation link ever sent. Nothing deleted any of it but deleting
 * the band, so the fastest-growing tables in the schema were the ones nobody had decided about —
 * and three of them hold something about a person.
 *
 * <p>It lives here rather than in a feature because it is one operator decision about several
 * features, and the app is the only module that may see them all; each feature still owns what
 * forgetting MEANS for its own data, behind its own port. Failed sync work is never touched,
 * whatever its age: it is the one thing in the outbox still waiting for someone.
 *
 * <p>Switched off with {@code gigsynchub.housekeeping.enabled=false} (tests). Scheduled the way
 * {@code PlatformCheckSchedule} is, and for the same reason — the next run is worked out BEFORE this
 * one starts, so a run that takes a long time cannot swallow the following night.
 */
@Component
class NightlyHousekeeping {

    private static final Logger log = LoggerFactory.getLogger(NightlyHousekeeping.class);

    private final ForgetOldSyncWorkUseCase syncWork;
    private final ForgetOldRecordsUseCase records;
    private final ForgetOldCalendarCopyUseCase calendarCopy;
    private final HousekeepingProperties properties;
    private final CronExpression cron;
    private final Clock clock;
    private final ScheduledExecutorService thread = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "housekeeping");
        t.setDaemon(true);
        return t;
    });

    NightlyHousekeeping(ForgetOldSyncWorkUseCase syncWork, ForgetOldRecordsUseCase records,
                        ForgetOldCalendarCopyUseCase calendarCopy, HousekeepingProperties properties, Clock clock) {
        this.syncWork = syncWork;
        this.records = records;
        this.calendarCopy = calendarCopy;
        this.properties = properties;
        this.cron = CronExpression.parse(properties.cron());
        this.clock = clock;
    }

    @PostConstruct
    void start() {
        if (properties.enabled()) {
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

    /**
     * The next night is booked first, so that however long this takes — or however it fails — the
     * following run still happens. Each kind is forgotten on its own: one of them failing is not a
     * reason for the others not to run.
     */
    private void run() {
        scheduleNext();
        forget("settled sync work", properties.settledSyncWorkDays(), syncWork::forgetSettledBefore);
        forget("audit events", properties.auditDays(), records::forgetAuditBefore);
        forget("expired invitations", properties.expiredInvitationDays(), records::forgetExpiredInvitationsBefore);
        forget("the saved calendar copy", properties.calendarCopyDays(), calendarCopy::forgetRemovedBefore);
    }

    private void forget(String what, int keepDays, java.util.function.ToIntFunction<Instant> forgetting) {
        Instant before = clock.instant().minus(keepDays, ChronoUnit.DAYS);
        try {
            int gone = forgetting.applyAsInt(before);
            if (gone > 0) {
                log.info("Housekeeping: forgot {} rows of {} from before {}", gone, what, before);
            }
        } catch (RuntimeException e) {
            log.error("Housekeeping: forgetting {} failed; it will be tried again tomorrow", what, e);
        }
    }
}
