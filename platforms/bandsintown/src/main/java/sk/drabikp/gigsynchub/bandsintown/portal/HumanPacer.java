package sk.drabikp.gigsynchub.bandsintown.portal;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Keeps the portal work looking like a person's rather than a machine's (the user's requirement for
 * Bandsintown): a pause of its own before every request, and a longer break between batches, so a run
 * never arrives as a burst. Nothing is sent in parallel for one band — one session works through its
 * requests in order, waiting in between.
 *
 * <p>An interrupted pause stops waiting and leaves the thread interrupted, so a shutdown isn't held up.
 */
public final class HumanPacer {

    private final long minMs;
    private final long maxMs;

    public HumanPacer(long minMs, long maxMs) {
        this.minMs = Math.max(0, minMs);
        this.maxMs = Math.max(this.minMs, maxMs);
    }

    /** A pause as between two things a person does. */
    public void pause() {
        sleep(between(minMs, maxMs));
    }

    /** A longer break, as between two batches. */
    public void longPause() {
        sleep(between(minMs * 3, maxMs * 4));
    }

    private static long between(long from, long to) {
        return to <= from ? from : ThreadLocalRandom.current().nextLong(from, to + 1);
    }

    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(Duration.ofMillis(millis));
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }
}
