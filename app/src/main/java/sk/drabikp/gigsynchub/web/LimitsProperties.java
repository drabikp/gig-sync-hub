package sk.drabikp.gigsynchub.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code gigsynchub.limits.*}: how much one user (or, before signing in, one address) may ask of the API in a
 * minute — {@code requests-per-minute} (600) of any kind, of which {@code changes-per-minute} (120) change
 * something. More is answered 429 ({@code tooManyRequests}).
 *
 * <p>{@code tracked-callers} (50 000) is how many of them are counted at once. The counting is in
 * memory, keyed by caller, and a key appears the moment a request does — so without a ceiling the
 * thing that bounds requests is itself unbounded, and a flood from many addresses fills the heap
 * instead of being refused. Past the ceiling the least recently seen caller is forgotten, which is
 * the one whose count matters least: whoever is flooding is by definition the most recently seen.
 */
@ConfigurationProperties("gigsynchub.limits")
public record LimitsProperties(Integer requestsPerMinute, Integer changesPerMinute, Integer trackedCallers) {

    public LimitsProperties {
        requestsPerMinute = requestsPerMinute == null || requestsPerMinute < 1 ? 600 : requestsPerMinute;
        changesPerMinute = changesPerMinute == null || changesPerMinute < 1 ? 120 : changesPerMinute;
        trackedCallers = trackedCallers == null || trackedCallers < 100 ? 50_000 : trackedCallers;
    }
}
