package sk.drabikp.bzscraper.browser;

import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Keeps a platform's browser open for a short while after a step is done with it, so the
 * next step for the same account (the next gig of a one-at-a-time run, the next task of the
 * pass) takes it over instead of starting a browser and checking the login again; one for
 * another account closes it (its profile is another one). Closed when nobody took it
 * within {@code idle}, and when the app (the JVM) shuts down. The platform's one browser at
 * a time is kept: a parked browser is only taken while holding {@code oneBrowser}, and only
 * closed by the idle timer when it can get it. Once closed, a browser handed back is closed
 * at once — nobody would take it any more.
 */
final class WarmBrowser {

    private static final Logger logger = LoggerFactory.getLogger(WarmBrowser.class);

    private final Semaphore oneBrowser;
    private final Duration idle;
    private final String name;
    private final ScheduledExecutorService timer;
    private WebDriver parked;
    private String parkedFor;
    private ScheduledFuture<?> closing;
    private boolean closed;
    private Thread shutdownHook;

    public WarmBrowser(Semaphore oneBrowser, Duration idle, String name) {
        this.oneBrowser = oneBrowser;
        this.idle = idle;
        this.name = name;
        this.timer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, name + "-browser-idle");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * The parked browser if it is {@code account}'s and still answers, else null (another account's is
     * closed). Call while holding {@code oneBrowser}.
     */
    public synchronized WebDriver take(String account) {
        if (closing != null) {
            closing.cancel(false);
            closing = null;
        }
        WebDriver driver = parked;
        boolean theirs = account.equals(parkedFor);
        parked = null;
        parkedFor = null;
        if (driver != null && (!theirs || !alive(driver))) {
            quit(driver);
            return null;
        }
        return driver;
    }

    /**
     * Keeps {@code account}'s browser for its next taker (call before releasing {@code oneBrowser}); closes it
     * instead when this is already closed (the app is shutting down).
     */
    public synchronized void park(WebDriver driver, String account) {
        if (closed) {
            quit(driver);
            return;
        }
        if (parked != null && parked != driver) {
            quit(parked);
        }
        parked = driver;
        parkedFor = account;
        closeOnShutdown();
        closing = timer.schedule(this::closeIfIdle, idle.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** Closes the parked browser now, and every one handed back later (app shutdown). */
    public synchronized void close() {
        closed = true;
        if (parked != null) {
            quit(parked);
            parked = null;
            parkedFor = null;
        }
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
            logger.debug("The JVM is shutting down; the {} browser is closed with the app", name);
        }
    }

    private void closeIfIdle() {
        if (!oneBrowser.tryAcquire()) {
            return;                                     // in use: whoever has it parks it again
        }
        try {
            synchronized (this) {
                if (parked != null) {
                    quit(parked);
                    parked = null;
                    parkedFor = null;
                }
            }
        } finally {
            oneBrowser.release();
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

    private static void quit(WebDriver driver) {
        try {
            driver.quit();
        } catch (RuntimeException e) {
            logger.debug("Closing a parked browser failed", e);
        }
    }
}
