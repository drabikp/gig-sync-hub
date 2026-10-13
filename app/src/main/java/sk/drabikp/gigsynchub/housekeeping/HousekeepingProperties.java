package sk.drabikp.gigsynchub.housekeeping;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code gigsynchub.housekeeping.*}: how long the tables that only ever grew keep what they hold,
 * and when the forgetting runs ({@code cron}, {@code enabled=false} in tests).
 *
 * <p>These are days, not a policy buried in code, because how long to keep a record of who changed
 * what is a decision for whoever runs the server — and one they may have to answer for.
 */
@ConfigurationProperties("gigsynchub.housekeeping")
public record HousekeepingProperties(Boolean enabled, String cron, Integer settledSyncWorkDays,
                                     Integer auditDays, Integer calendarCopyDays, Integer expiredInvitationDays) {

    public HousekeepingProperties {
        enabled = enabled == null || enabled;
        cron = cron == null || cron.isBlank() ? "0 15 4 * * *" : cron;       // before the nightly check
        settledSyncWorkDays = settledSyncWorkDays == null ? 90 : settledSyncWorkDays;
        auditDays = auditDays == null ? 365 : auditDays;
        calendarCopyDays = calendarCopyDays == null ? 180 : calendarCopyDays;
        expiredInvitationDays = expiredInvitationDays == null ? 30 : expiredInvitationDays;
    }
}
