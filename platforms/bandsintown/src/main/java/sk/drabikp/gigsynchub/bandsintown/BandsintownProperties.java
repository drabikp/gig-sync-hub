package sk.drabikp.gigsynchub.bandsintown;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code gigsynchub.bandsintown.*}: where the artist portal is and how human the pace of its requests is
 * ({@link sk.drabikp.gigsynchub.bandsintown.portal.HumanPacer}). Nothing secret lives here — each band
 * connects its own account on the page, and the server keeps only a session it can revoke.
 */
@ConfigurationProperties("gigsynchub.bandsintown")
public record BandsintownProperties(String baseUrl, Pacing pacing) {

    /** {@code pacing.min-ms} / {@code max-ms}: the human pauses between steps (see {@link HumanPacer}). */
    public record Pacing(Long minMs, Long maxMs) {

        public Pacing {
            minMs = minMs == null ? 900 : minMs;
            maxMs = maxMs == null ? 2600 : maxMs;
        }
    }

    public BandsintownProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://artists.bandsintown.com" : baseUrl;
        pacing = pacing == null ? new Pacing(null, null) : pacing;
    }

}
