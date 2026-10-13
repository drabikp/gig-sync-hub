package sk.drabikp.gigsynchub.portal;

import org.springframework.core.env.PropertyResolver;

import java.util.Locale;

/**
 * How this server talks to a platform, {@code gigsynchub.<platform>.client}: not at all ({@code off}, the
 * stub — the default), or with plain requests ({@code http}: what the platform's own pages send). No
 * browser runs here any more, so there is nothing else to be.
 *
 * <p>A server still carrying the older switch {@code gigsynchub.<platform>.selenium.enabled=true} keeps
 * driving the platform — the only way to drive one is now {@code http} — so an old deployment doesn't go
 * quiet on the way; its setting is worth renaming all the same.
 */
public enum PlatformClient {
    OFF,
    HTTP;

    /** The client {@code platform} (as in its properties' prefix, e.g. {@code bandzone}) is configured with. */
    public static PlatformClient of(PropertyResolver properties, String platform) {
        String prefix = "gigsynchub." + platform;
        String chosen = properties.getProperty(prefix + ".client", "").strip();
        if (chosen.isEmpty()) {
            return properties.getProperty(prefix + ".selenium.enabled", Boolean.class, false) ? HTTP : OFF;
        }
        try {
            return valueOf(chosen.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException _) {
            throw new IllegalStateException(prefix + ".client must be off or http, not '" + chosen + "'");
        }
    }

    /** Whether the platform is driven at all — its account then needs to be connected to publish. */
    public boolean drives() {
        return this != OFF;
    }
}
