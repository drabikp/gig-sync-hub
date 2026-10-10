package sk.drabikp.bzscraper.browser;

import org.junit.jupiter.api.Assumptions;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.File;
import java.nio.file.Path;

/**
 * The headless Chromium the page tests run in (pages served from fixtures, never the real
 * platforms). A test that needs it is skipped where none is installed.
 */
public final class TestChromium {

    /** {@code -Dbzscraper.test.chromium=…} where Chromium is elsewhere. */
    public static final String CHROMIUM = System.getProperty("bzscraper.test.chromium", "/usr/bin/chromium");
    /** {@code -Dbzscraper.test.chromedriver=…} likewise. */
    public static final String CHROMEDRIVER = System.getProperty("bzscraper.test.chromedriver", "/usr/bin/chromedriver");

    private TestChromium() {
    }

    /** Skips the calling test when Chromium or chromedriver is missing. */
    public static void assumeInstalled() {
        Assumptions.assumeTrue(new File(CHROMIUM).canExecute() && new File(CHROMEDRIVER).canExecute(),
                "needs Chromium and chromedriver");
    }

    /** A new headless browser with a throwaway profile. */
    public static ChromeDriver start(String windowSize) {
        assumeInstalled();
        ChromeOptions options = new ChromeOptions();
        options.setBinary(CHROMIUM);
        options.addArguments("--headless=new", "--no-sandbox", "--disable-dev-shm-usage", "--window-size=" + windowSize);
        return new ChromeDriver(new ChromeDriverService.Builder().usingDriverExecutable(new File(CHROMEDRIVER))
                .build(), options);
    }

    /** {@link PlatformBrowser} settings for the test Chromium, its accounts' profiles in {@code profileRoot}. */
    public static PlatformBrowser.Settings settings(String platform, Path profileRoot) {
        return new PlatformBrowser.Settings(platform, profileRoot.toString(), true, CHROMIUM, CHROMEDRIVER, "basic",
                "1280,1024", 1);
    }
}
