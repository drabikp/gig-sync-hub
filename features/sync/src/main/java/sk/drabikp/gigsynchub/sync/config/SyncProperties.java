package sk.drabikp.gigsynchub.sync.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code gigsynchub.sync.*}: the workers ({@code worker.enabled}, off in tests: tasks queue but never run;
 * {@code worker.threads} lanes run at once; {@code poll-seconds}) and the breakers ({@code breaker.failures}
 * batches failing in a row hold a band's work on a platform back {@code breaker.cooldown-minutes};
 * {@code breaker.platform-bands} bands held back on one platform at once hold the platform back for all).
 * {@code stranded-after-minutes}: how long a RUNNING task no worker holds is left alone before it is
 * settled — longer than any one pass of a step, which is minutes of paced requests.
 */
@ConfigurationProperties("gigsynchub.sync")
public record SyncProperties(Worker worker, Long pollSeconds, Long strandedAfterMinutes, Breaker breaker) {

    public record Worker(Boolean enabled, Integer threads) {

        public Worker {
            enabled = enabled == null || enabled;
            threads = threads == null || threads < 1 ? 2 : threads;
        }
    }

    public record Breaker(Integer failures, Long cooldownMinutes, Integer platformBands) {

        public Breaker {
            failures = failures == null ? 3 : failures;
            cooldownMinutes = cooldownMinutes == null ? 30 : cooldownMinutes;
            platformBands = platformBands == null ? 3 : platformBands;
        }
    }

    public SyncProperties {
        worker = worker == null ? new Worker(null, null) : worker;
        pollSeconds = pollSeconds == null ? 15 : pollSeconds;
        strandedAfterMinutes = strandedAfterMinutes == null ? 30 : strandedAfterMinutes;
        breaker = breaker == null ? new Breaker(null, null, null) : breaker;
    }
}
