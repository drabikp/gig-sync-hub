package sk.drabikp.bzscraper.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code bzscraper.limits.*}: how much one user (or, before signing in, one address) may ask of the API in a
 * minute — {@code requests-per-minute} (600) of any kind, of which {@code changes-per-minute} (120) change
 * something. More is answered 429 ({@code tooManyRequests}).
 */
@ConfigurationProperties("bzscraper.limits")
public record LimitsProperties(Integer requestsPerMinute, Integer changesPerMinute) {

    public LimitsProperties {
        requestsPerMinute = requestsPerMinute == null || requestsPerMinute < 1 ? 600 : requestsPerMinute;
        changesPerMinute = changesPerMinute == null || changesPerMinute < 1 ? 120 : changesPerMinute;
    }
}
