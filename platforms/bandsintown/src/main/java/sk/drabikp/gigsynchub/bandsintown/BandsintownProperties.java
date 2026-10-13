package sk.drabikp.gigsynchub.bandsintown;

import org.springframework.boot.context.properties.ConfigurationProperties;
import sk.drabikp.gigsynchub.portal.PlatformEgress;

/**
 * {@code gigsynchub.bandsintown.*}: where the artist portal is and how human the pace of its requests is
 * ({@link sk.drabikp.gigsynchub.bandsintown.portal.HumanPacer}). Nothing secret lives here — each band
 * connects its own account on the page, and the server keeps only a session it can revoke.
 */
@ConfigurationProperties("gigsynchub.bandsintown")
public record BandsintownProperties(String baseUrl, Pacing pacing, Proxy proxy) {

    /**
     * Where this server's Bandsintown traffic comes FROM, when it should not be this machine's own
     * address: {@code gigsynchub.bandsintown.proxy.host/port/username/password}. See
     * {@link PlatformEgress} for what this is for and, more to the point, what it is not.
     */
    public record Proxy(String host, Integer port, String username, String password) {
        public Proxy {
            port = port == null ? 8080 : port;
        }

        PlatformEgress egress() {
            return new PlatformEgress(host, port, username, password);
        }

        @Override
        public String toString() {
            return egress().toString();
        }
    }

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
        proxy = proxy == null ? new Proxy(null, null, null, null) : proxy;
    }

    /** Straight out of this machine unless a proxy is configured. */
    public PlatformEgress egress() {
        return proxy.egress();
    }

}
