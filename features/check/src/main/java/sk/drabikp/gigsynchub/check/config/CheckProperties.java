package sk.drabikp.gigsynchub.check.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@code gigsynchub.check.*}: the nightly platform check — on unless {@code enabled=false}, when
 * ({@code cron}, default 04:30), how far back past gigs are compared ({@code past-days}), and how
 * long one night's walk through the bands may take ({@code budget}, 3 h).
 *
 * <p>The budget must be shorter than the gap between two runs, or a round that spends all of it
 * runs into the next one. Every band's check is a read of every platform it is on, over the
 * network and paced like a person, so the night's length is the bands' number times that — a
 * number this server does not get to choose. What it does get to choose is stopping, and carrying
 * on tomorrow where it stopped, rather than still reading at nine in the morning.
 */
@ConfigurationProperties("gigsynchub.check")
public record CheckProperties(Boolean enabled, String cron, Integer pastDays, Duration budget) {

    public CheckProperties {
        enabled = enabled == null || enabled;
        cron = cron == null || cron.isBlank() ? "0 30 4 * * *" : cron;
        pastDays = pastDays == null ? 60 : pastDays;
        budget = budget == null || budget.isNegative() || budget.isZero() ? Duration.ofHours(3) : budget;
    }
}
