package sk.drabikp.bzscraper.browser;

import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A platform's browsers (Chromium), for every operation on that platform:
 * <ul>
 *   <li>a private profile per account (a band's login there) holding its saved login
 *       ({@link BrowserProfile}): every lease names its account, and its browser is that
 *       account's — never another band's;</li>
 *   <li>one session per account at a time (a profile can't be opened twice, and a person has
 *       one tab open), at most {@code sessions} on the platform at once (many accounts from one
 *       address look like a bot farm), and the server's limit on open browsers
 *       ({@link BrowserPool}); {@link #acquire} waits as long as the caller is willing to;</li>
 *   <li>kept warm a minute after a session ({@link WarmBrowser}) for the account's next one;</li>
 *   <li>the same Chromium options for every platform, plus what {@link Customizer} adds;</li>
 *   <li>a screenshot of the page an operation failed on, in the temp directory
 *       ({@code bzscraper-<platform>-<step>.png});</li>
 *   <li>what a failure in the browser means, in the platform's own exception ({@link Failures}):
 *       an unexpected browser error is temporary; a busy browser, or an operation interrupted
 *       (the app is stopping), is busy — again later.</li>
 * </ul>
 * The platform's client keeps only what is its own: logging in and its pages.
 */
public final class PlatformBrowser {

    private static final Logger logger = LoggerFactory.getLogger(PlatformBrowser.class);
    private static final Duration KEEP_WARM = Duration.ofSeconds(60);

    /**
     * Where the browser is and how it starts; {@code profileRoot} holds a directory per account (blank: the
     * default, see {@link BrowserProfile#dir}); {@code sessions}: how many accounts' sessions at once.
     */
    public record Settings(String platform, String profileRoot, boolean headless, String chromiumBinary,
                           String chromedriverPath, String passwordStore, String windowSize, int sessions) {

        public Settings {
            sessions = Math.max(1, sessions);
        }
    }

    /** What a platform adds to a new browser (options before it starts, set-up after). */
    public interface Customizer {

        default void options(ChromeOptions options) {
        }

        default void started(ChromeDriver driver) {
        }
    }

    /** Work done in the browser that may fail with the platform's own exception. */
    @FunctionalInterface
    public interface Work<T, E extends Exception> {
        T run() throws E;
    }

    /** Logs in in a leased browser (or finds the saved login still valid) and makes the platform's session of it. */
    @FunctionalInterface
    public interface Login<S, E extends Exception> {
        S open(Lease lease) throws E;
    }

    /**
     * The platform's own exception for what the browser can fail with: a {@code temporary}
     * failure (a missing element, a timeout — this step again, later) and {@code busy} (not
     * done: the browser is in use, or the operation was interrupted — again later, no attempt
     * used). {@code platform} is its name in the messages.
     */
    public record Failures<E extends Exception>(String platform, BiFunction<String, RuntimeException, E> temporary,
                                                Function<String, E> busy) {
    }

    private final Settings settings;
    private final Customizer customizer;
    private final BrowserPool pool;
    private final Function<String, WebDriver> starter;
    private final Semaphore sessions;
    private final Map<String, Semaphore> accounts = new ConcurrentHashMap<>();
    private final WarmBrowser warm;

    public PlatformBrowser(Settings settings, Customizer customizer, BrowserPool pool) {
        this(settings, customizer, pool, null);
    }

    /** {@code starter} starts an account's browser (null: Chromium as {@code settings} say) — tests give their own. */
    PlatformBrowser(Settings settings, Customizer customizer, BrowserPool pool, Function<String, WebDriver> starter) {
        this.settings = settings;
        this.customizer = customizer;
        this.pool = pool;
        this.starter = starter != null ? starter : this::start;
        this.sessions = new Semaphore(settings.sessions());
        this.warm = new WarmBrowser(KEEP_WARM, settings.platform(), this::quit);
        pool.register(settings.platform(), warm, this::sessionsInUse);
    }

    /**
     * The browser for {@code account} — its warm one, else a new one with the account's profile — when,
     * within {@code wait}, the account has no other session, the platform has room for one more and the
     * server for its browser; empty otherwise (or when the wait was interrupted). The lease must end with
     * {@link Lease#keepWarm} or {@link Lease#discard}.
     */
    public Optional<Lease> acquire(String account, Duration wait) {
        long deadline = System.nanoTime() + wait.toNanos();
        Semaphore own = accounts.computeIfAbsent(account, a -> new Semaphore(1));
        if (!tryAcquire(own, deadline)) {
            return Optional.empty();
        }
        if (!tryAcquire(sessions, deadline)) {
            own.release();
            return Optional.empty();
        }
        try {
            WebDriver driver = warm.take(account);
            if (driver != null) {
                return Optional.of(new Lease(driver, account, true));
            }
            if (!pool.reserve(Duration.ofNanos(Math.max(0, deadline - System.nanoTime())))) {
                sessions.release();
                own.release();
                return Optional.empty();
            }
            try {
                return Optional.of(new Lease(starter.apply(account), account, false));
            } catch (RuntimeException e) {
                pool.closed();
                throw e;
            }
        } catch (RuntimeException e) {
            sessions.release();
            own.release();
            throw e;
        }
    }

    /** The platform's sessions running right now. */
    public int sessionsInUse() {
        return settings.sessions() - sessions.availablePermits();
    }

    private static boolean tryAcquire(Semaphore semaphore, long deadline) {
        try {
            return semaphore.tryAcquire(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Closes a browser for good and frees its room on the server. Never throws. */
    private void quit(WebDriver driver) {
        try {
            driver.quit();
        } catch (RuntimeException e) {
            logger.debug("Closing a {} browser failed", settings.platform(), e);
        } finally {
            pool.closed();
        }
    }

    /**
     * Opens a session for {@code account}: the browser when it is free within {@code wait} (else
     * {@code busy}), then {@code login} logs in and makes the session. A failed login leaves a
     * screenshot and discards the browser (its state can't be trusted).
     */
    public <S, E extends Exception> S open(String account, Duration wait, Failures<E> failures, Login<S, E> login)
            throws E {
        Optional<Lease> leased;
        try {
            leased = acquire(account, wait);
        } catch (RuntimeException e) {
            throw failure(failures, "The " + failures.platform() + " browser did not start", e);
        }
        Lease lease = leased.orElseThrow(() -> failures.busy().apply("Another " + failures.platform()
                + " operation is using the browser (this band's, or the server's browsers are all in use)."));
        try {
            return login.open(lease);
        } catch (RuntimeException e) {
            lease.discard("login");
            // the browser's own message may quote the script arguments — the password typed in: only its type
            throw failure(failures, failures.platform() + " login failed",
                    new IllegalStateException(e.getClass().getSimpleName()));
        } catch (Exception e) {
            lease.discard("login");
            throw e;
        }
    }

    /** Closes the warm browsers, and any handed back after this (app shutdown). */
    public void close() {
        warm.close();
    }

    private WebDriver start(String account) {
        Path profile = BrowserProfile.dir(settings.profileRoot(), settings.platform(), account);
        try {
            BrowserProfile.ensurePrivate(profile);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create the " + settings.platform() + " browser profile "
                    + profile, e);
        }
        ChromeOptions options = new ChromeOptions();
        if (settings.headless()) {
            options.addArguments("--headless=new");
        }
        options.addArguments("--no-sandbox", "--disable-dev-shm-usage", "--window-size=" + settings.windowSize(),
                "--user-data-dir=" + profile);
        options.addArguments(BrowserProfile.secretStoreArgs(settings.passwordStore()));
        if (!isBlank(settings.chromiumBinary())) {
            options.setBinary(settings.chromiumBinary());
        }
        customizer.options(options);
        ChromeDriver driver = isBlank(settings.chromedriverPath()) ? new ChromeDriver(options)
                : new ChromeDriver(new ChromeDriverService.Builder()
                        .usingDriverExecutable(new File(settings.chromedriverPath())).build(), options);
        try {
            customizer.started(driver);
        } catch (RuntimeException e) {
            driver.quit();
            throw e;
        }
        return driver;
    }

    /** The options that hide automation from a platform that watches for it (see Bandsintown's pacing). */
    public static void looksLikeAPerson(ChromeOptions options, String language) {
        options.addArguments("--lang=" + language, "--disable-blink-features=AutomationControlled");
        options.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
        options.setExperimentalOption("prefs", Map.of("intl.accept_languages", language + ",en"));
    }

    /** Headless Chrome names itself in the user agent — present a normal one. */
    public static void normalUserAgent(ChromeDriver driver, String language) {
        String userAgent = String.valueOf(driver.executeScript("return navigator.userAgent;"));
        driver.executeCdpCommand("Network.setUserAgentOverride",
                Map.of("userAgent", userAgent.replace("HeadlessChrome", "Chrome"), "acceptLanguage", language + ",en"));
    }

    /**
     * An unexpected browser error as the platform's failure: temporary — or busy when the
     * thread was interrupted (the app is stopping, the worker's pacing cut short): nothing was
     * wrong with the platform, so it is postponed like a busy browser, and the interrupt stays
     * set for whoever stops the thread.
     */
    private static <E extends Exception> E failure(Failures<E> failures, String failure, RuntimeException e) {
        if (Thread.currentThread().isInterrupted()) {
            return failures.busy().apply(failure + ": interrupted — again later");
        }
        return failures.temporary().apply(failure + ": " + e.getMessage(), e);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** One operation's hold on the browser. */
    public final class Lease {

        private final WebDriver driver;
        private final String account;
        private final boolean wasWarm;
        private boolean ended;

        private Lease(WebDriver driver, String account, boolean wasWarm) {
            this.driver = driver;
            this.account = account;
            this.wasWarm = wasWarm;
        }

        public WebDriver driver() {
            return driver;
        }

        /** True when it is the warm browser of the account's earlier session (its login is probably still valid). */
        public boolean wasWarm() {
            return wasWarm;
        }

        /** Done: the browser stays open a little for the account's next operation. Never throws. */
        public synchronized void keepWarm() {
            if (!ended) {
                ended = true;
                try {
                    warm.park(driver, account);
                } catch (RuntimeException e) {
                    logger.warn("Keeping the {} browser open failed — closing it", settings.platform(), e);
                    quit(driver);
                } finally {
                    release();
                }
            }
        }

        /** Done, and the browser is not to be trusted (a failed login): closed. Never throws. */
        public synchronized void discard() {
            if (!ended) {
                ended = true;
                try {
                    quit(driver);
                } finally {
                    release();
                }
            }
        }

        private void release() {
            sessions.release();
            accounts.get(account).release();
        }

        /** Keeps a picture of the page {@code step} failed on, then discards the browser. */
        private void discard(String step) {
            if (!Thread.currentThread().isInterrupted()) {
                screenshot(step);
            }
            discard();
        }

        /** Keeps a picture of the current page for diagnosis. */
        public void screenshot(String step) {
            try {
                Path target = Paths.get(System.getProperty("java.io.tmpdir"),
                        "bzscraper-" + settings.platform() + "-" + step + ".png");
                Files.write(target, ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES));
                logger.warn("{} {} failed at {} — screenshot: {}", settings.platform(), step,
                        driver.getCurrentUrl(), target);
            } catch (IOException | RuntimeException e) {
                logger.debug("Could not save a screenshot", e);
            }
        }

        /**
         * Runs one operation: any failure leaves a screenshot; an unexpected browser error
         * becomes the platform's temporary failure ({@code failure}: what failed), or a busy one
         * when the operation was interrupted.
         */
        public <T, E extends Exception> T guarded(String step, String failure, Failures<E> failures, Work<T, E> work)
                throws E {
            try {
                return work.run();
            } catch (RuntimeException e) {
                if (!Thread.currentThread().isInterrupted()) {
                    screenshot(step);
                }
                throw failure(failures, failure, e);
            } catch (Exception e) {
                screenshot(step);
                throw e;
            }
        }

    }
}
