package sk.drabikp.bzscraper.browser;

import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Keeps a platform's browsers open for a short while after a session is done with them, each for its
 * account (a band's login): the account's next session (the next gig of a one-at-a-time run, the next task)
 * takes its browser over instead of starting one and checking the login again. A parked browser is closed
 * when nobody took it within {@code idle}, when the server needs room for another ({@link #closeOldest}),
 * and when the app (the JVM) shuts down. Once closed, a browser handed back is closed at once.
 */
final class WarmBrowser {

    private static final Logger logger = LoggerFactory.getLogger(WarmBrowser.class);

    private record Parked(WebDriver driver, Instant since, ScheduledFuture<?> closing) {
    }

    private final Duration idle;
    private final String name;
    private final Consumer<WebDriver> quit;
    private final ScheduledExecutorService timer;
    private final Map<String, Parked> parked = new HashMap<>();
    private boolean closed;
    private Thread shutdownHook;

    /** {@code quit} closes a browser for good (and frees its room on the server). */
    WarmBrowser(Duration idle, String name, Consumer<WebDriver> quit) {
        this.idle = idle;
        this.name = name;
        this.quit = quit;
        this.timer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, name + "-browser-idle");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** The account's parked browser if it still answers, else null. */
    synchronized WebDriver take(String account) {
        Parked taken = parked.remove(account);
        if (taken == null) {
            return null;
        }
        taken.closing().cancel(false);
        if (!alive(taken.driver())) {
            quit.accept(taken.driver());
            return null;
        }
        return taken.driver();
    }

    /** Keeps the account's browser for its next session; closes it instead when this is closed (the app is stopping). */
    synchronized void park(WebDriver driver, String account) {
        if (closed) {
            quit.accept(driver);
            return;
        }
        Parked before = parked.remove(account);
        if (before != null && before.driver() != driver) {
            before.closing().cancel(false);
            quit.accept(before.driver());
        }
        closeOnShutdown();
        ScheduledFuture<?> closing = timer.schedule(() -> closeIfIdle(account, driver), idle.toMillis(),
                TimeUnit.MILLISECONDS);
        parked.put(account, new Parked(driver, Instant.now(), closing));
    }

    /** Since when the longest idle browser waits; empty when none does. */
    synchronized Optional<Instant> oldestParked() {
        return parked.values().stream().map(Parked::since).min(Comparator.naturalOrder());
    }

    /** Closes the browser idle the longest (the server needs room); false when none is parked. */
    synchronized boolean closeOldest() {
        Optional<Map.Entry<String, Parked>> oldest = parked.entrySet().stream()
                .min(Comparator.comparing(e -> e.getValue().since()));
        if (oldest.isEmpty()) {
            return false;
        }
        parked.remove(oldest.get().getKey());
        oldest.get().getValue().closing().cancel(false);
        quit.accept(oldest.get().getValue().driver());
        return true;
    }

    /** How many browsers are parked. */
    synchronized int size() {
        return parked.size();
    }

    /** Closes every parked browser now, and every one handed back later (app shutdown). */
    synchronized void close() {
        closed = true;
        parked.values().forEach(p -> {
            p.closing().cancel(false);
            quit.accept(p.driver());
        });
        parked.clear();
        timer.shutdownNow();
        if (shutdownHook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException e) {
                // the JVM is shutting down — this may be the hook itself
            }
            shutdownHook = null;
        }
    }

    /**
     * A parked browser must not outlive the app (it would hold the profile) — also when the
     * client is used outside Spring (live tests). Registered with the first parked browser.
     */
    private void closeOnShutdown() {
        if (shutdownHook != null) {
            return;
        }
        Thread hook = new Thread(this::close, name + "-browser-shutdown");
        try {
            Runtime.getRuntime().addShutdownHook(hook);
            shutdownHook = hook;
        } catch (IllegalStateException e) {
            logger.debug("The JVM is shutting down; the {} browsers are closed with the app", name);
        }
    }

    private synchronized void closeIfIdle(String account, WebDriver driver) {
        Parked now = parked.get(account);
        if (now != null && now.driver() == driver) {
            parked.remove(account);
            quit.accept(driver);
        }
    }

    private static boolean alive(WebDriver driver) {
        try {
            driver.getWindowHandle();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
