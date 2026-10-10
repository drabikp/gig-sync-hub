package sk.drabikp.bzscraper.bandsintown;

import org.springframework.boot.context.properties.ConfigurationProperties;
import sk.drabikp.bzscraper.browser.SeleniumOptions;

/**
 * {@code bzscraper.bandsintown.*}: where the artist portal is, whether this server drives it
 * ({@code selenium.enabled}) and how human its pace is. Each band connects its own account on the page;
 * {@code login}, {@code password}, {@code totp-secret}, {@code artist-name}, {@code artist-id} and
 * {@code notify-followers} are band #1's from before that, kept once on start ({@link BandsintownPlatform}).
 * Secrets never leave this record ({@link #toString} hides them).
 */
@ConfigurationProperties("bzscraper.bandsintown")
public record BandsintownProperties(String baseUrl, String login, String password, String totpSecret,
                                    String artistId, String artistName, boolean notifyFollowers,
                                    SeleniumOptions selenium, Pacing pacing) {

    /** {@code pacing.min-ms} / {@code max-ms}: the human pauses between steps (see {@link HumanPacer}). */
    public record Pacing(Long minMs, Long maxMs) {

        public Pacing {
            minMs = minMs == null ? 900 : minMs;
            maxMs = maxMs == null ? 2600 : maxMs;
        }
    }

    public BandsintownProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://artists.bandsintown.com" : baseUrl;
        login = login == null ? "" : login.strip();
        password = password == null ? "" : password;
        totpSecret = totpSecret == null ? "" : totpSecret;
        artistId = artistId == null ? "" : artistId;
        artistName = artistName == null ? "" : artistName;
        selenium = selenium == null ? SeleniumOptions.OFF : selenium;
        pacing = pacing == null ? new Pacing(null, null) : pacing;
    }

    @Override
    public String toString() {
        return "BandsintownProperties[baseUrl=" + baseUrl + ", artistId=" + artistId + ", artistName=" + artistName
                + ", notifyFollowers=" + notifyFollowers + ", login=" + (login.isBlank() ? "" : "***")
                + ", password=" + (password.isBlank() ? "" : "***") + ", totpSecret="
                + (totpSecret.isBlank() ? "" : "***") + ", selenium=" + selenium + ", pacing=" + pacing + "]";
    }
}
