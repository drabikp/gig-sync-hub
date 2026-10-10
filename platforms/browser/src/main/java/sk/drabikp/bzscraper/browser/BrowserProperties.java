package sk.drabikp.bzscraper.browser;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code bzscraper.browser.*}: what every platform's browser shares — where saved logins are
 * encrypted ({@link BrowserProfile#secretStoreArgs}), unless a platform sets its own, where
 * Chromium and chromedriver are, and how many browsers this server opens at once ({@code max},
 * 2 — {@link BrowserPool}).
 */
@ConfigurationProperties("bzscraper.browser")
public record BrowserProperties(String passwordStore, String chromiumBinary, String chromedriver, Integer max) {

    public BrowserProperties {
        max = max == null || max < 1 ? 2 : max;
        passwordStore = passwordStore == null || passwordStore.isBlank() ? "auto" : passwordStore;
        chromiumBinary = chromiumBinary == null ? "" : chromiumBinary;
        chromedriver = chromedriver == null ? "" : chromedriver;
    }
}
