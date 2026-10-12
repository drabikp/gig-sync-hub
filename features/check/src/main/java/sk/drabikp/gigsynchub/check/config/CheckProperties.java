package sk.drabikp.gigsynchub.check.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code gigsynchub.check.*}: the nightly platform check — on unless {@code enabled=false}, when
 * ({@code cron}, default 04:30), and how far back past gigs are compared ({@code past-days}).
 */
@ConfigurationProperties("gigsynchub.check")
public record CheckProperties(Boolean enabled, String cron, Integer pastDays) {

    public CheckProperties {
        enabled = enabled == null || enabled;
        cron = cron == null || cron.isBlank() ? "0 30 4 * * *" : cron;
        pastDays = pastDays == null ? 60 : pastDays;
    }
}
