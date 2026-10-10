package sk.drabikp.bzscraper.browser;

import org.springframework.core.env.PropertyResolver;

import java.util.Locale;

/**
 * How this server talks to a platform, {@code bzscraper.<platform>.client}: not at all ({@code off}, the
 * stub — the default), with plain requests ({@code http}: what the platform's own pages send, no browser)
 * or by driving its pages in Chromium ({@code selenium}, the fallback). Unset, the older switch
 * {@code bzscraper.<platform>.selenium.enabled=true} still means {@code selenium}.
 */
public enum PlatformClient {
    OFF,
    HTTP,
    SELENIUM;

    /** The client {@code platform} (as in its properties' prefix, e.g. {@code bandzone}) is configured with. */
    public static PlatformClient of(PropertyResolver properties, String platform) {
        String prefix = "bzscraper." + platform;
        String chosen = properties.getProperty(prefix + ".client", "").strip();
        if (chosen.isEmpty()) {
            return properties.getProperty(prefix + ".selenium.enabled", Boolean.class, false) ? SELENIUM : OFF;
        }
        try {
            return valueOf(chosen.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(prefix + ".client must be off, http or selenium, not '" + chosen + "'");
        }
    }

    /** Whether the platform is driven at all — its account then needs to be connected to publish. */
    public boolean drives() {
        return this != OFF;
    }
}
