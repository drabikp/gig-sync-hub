package sk.drabikp.bzscraper.browser;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.IntSupplier;

/**
 * The server's browsers, every platform's together: at most {@code max} Chromiums open at once — a
 * Chromium is 300–500 MB — the warm ones (kept a minute for their account's next session) included. When
 * the server is full and a new browser is needed, the warm one idle the longest (of any platform) is
 * closed to make room; when every one is in use, the new one waits.
 */
public final class BrowserPool {

    private static final long POLL_MILLIS = 200;

    private final int max;
    private final List<WarmBrowser> warm = new CopyOnWriteArrayList<>();
    private final Map<String, IntSupplier> sessions = new ConcurrentSkipListMap<>();
    private int open;

    public BrowserPool(int max) {
        if (max < 1) {
            throw new IllegalArgumentException("at least one browser");
        }
        this.max = max;
    }

    /**
     * Keeps track of a platform's warm browsers, to close the idlest one when the server is full, and of its
     * sessions running.
     */
    void register(String platform, WarmBrowser browsers, IntSupplier sessionsInUse) {
        warm.add(browsers);
        sessions.put(platform, sessionsInUse);
    }

    /** Each platform's sessions running right now. */
    public Map<String, Integer> sessionsInUse() {
        Map<String, Integer> now = new TreeMap<>();
        sessions.forEach((platform, inUse) -> now.put(platform, inUse.getAsInt()));
        return now;
    }

    /**
     * Room for one more browser within {@code wait} — closing the longest idle warm one when the server is
     * full; false when every browser stayed in use (or the wait was interrupted). Every browser started
     * after a true must be {@link #closed} once.
     */
    boolean reserve(Duration wait) {
        long deadline = System.nanoTime() + wait.toNanos();
        while (true) {
            synchronized (this) {
                if (open < max) {
                    open++;
                    return true;
                }
            }
            Optional<WarmBrowser> idlest = warm.stream().filter(w -> w.oldestParked().isPresent())
                    .min(Comparator.comparing(w -> w.oldestParked().orElseThrow()));
            if (idlest.isPresent() && idlest.get().closeOldest()) {
                continue;                                   // made room (closed() was called)
            }
            long left = deadline - System.nanoTime();
            if (left <= 0) {
                return false;
            }
            synchronized (this) {
                try {
                    wait(Math.max(1, Math.min(POLL_MILLIS, left / 1_000_000)));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
    }

    /** A browser was closed (or didn't start after all): room for another. */
    synchronized void closed() {
        open = Math.max(0, open - 1);
        notifyAll();
    }

    /** The browsers open right now, warm ones included. */
    public synchronized int open() {
        return open;
    }

    public int max() {
        return max;
    }
}
