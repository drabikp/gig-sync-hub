package sk.drabikp.bzscraper.sync.domain;

import org.jmolecules.ddd.annotation.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * When a failed sync task runs again. Repeatable actions (update, cancel, delete) are
 * retried after 1, 5 and 15 minutes — platform hiccups and timeouts usually pass — then
 * left for the user. Publishing and reactivating are never retried automatically: a
 * failure may have created the event anyway, and a retry would duplicate it. A failure
 * that can't go away by waiting ({@code permanent}, e.g. "not supported") isn't retried.
 */
@Service
public final class SyncRetryPolicy {

    private static final List<Duration> BACKOFF = List.of(
            Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15));

    /** A step that couldn't start (the platform's browser busy with a read) runs again after this. */
    public static final Duration POSTPONE = Duration.ofMinutes(1);

    private SyncRetryPolicy() {
    }

    /**
     * @param attemptsMade attempts including the one that just failed
     * @return when to try again, or empty to give up
     */
    public static Optional<Instant> nextAttempt(SyncAction action, int attemptsMade, Instant now) {
        if (!action.repeatable() || attemptsMade < 1 || attemptsMade > BACKOFF.size()) {
            return Optional.empty();
        }
        return Optional.of(now.plus(BACKOFF.get(attemptsMade - 1)));
    }

    /** Attempts a repeatable task gets before it is left for the user. */
    public static int maxAttempts() {
        return BACKOFF.size() + 1;
    }
}
