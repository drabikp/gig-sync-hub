package sk.drabikp.bzscraper.browser;

/**
 * A platform's browser switches, {@code bzscraper.<platform>.selenium.*}: whether the platform
 * is driven at all on this server, headless (default) or visible, its own Chromium/chromedriver
 * (blank: the shared {@link BrowserProperties}), where its accounts' profiles go (blank:
 * {@code ~/.bzscraper/<platform>-browser}; one directory per account in it, see {@link BrowserProfile#dir})
 * and how many accounts' sessions run at once from this server ({@code max-sessions}, 1).
 */
public record SeleniumOptions(boolean enabled, Boolean headless, String chromiumBinary, String chromedriver,
                              String profileDir, Integer maxSessions) {

    /** Off, headless, everything else default. */
    public static final SeleniumOptions OFF = new SeleniumOptions(false, null, null, null, null, null);

    public SeleniumOptions {
        headless = headless == null || headless;
        chromiumBinary = chromiumBinary == null ? "" : chromiumBinary;
        chromedriver = chromedriver == null ? "" : chromedriver;
        profileDir = profileDir == null ? "" : profileDir;
        maxSessions = maxSessions == null || maxSessions < 1 ? 1 : maxSessions;
    }

    /** The {@link PlatformBrowser} settings for {@code platform}. */
    public PlatformBrowser.Settings browser(String platform, BrowserProperties shared, String windowSize) {
        return new PlatformBrowser.Settings(platform, profileDir, headless,
                chromiumBinary.isBlank() ? shared.chromiumBinary() : chromiumBinary,
                chromedriver.isBlank() ? shared.chromedriver() : chromedriver, shared.passwordStore(), windowSize,
                maxSessions);
    }
}
