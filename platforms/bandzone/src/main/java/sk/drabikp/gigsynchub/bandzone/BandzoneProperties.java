package sk.drabikp.gigsynchub.bandzone;

import org.springframework.boot.context.properties.ConfigurationProperties;
import sk.drabikp.gigsynchub.portal.PlatformEgress;

/**
 * {@code gigsynchub.bandzone.*}: where Bandzone is (whether this server drives it is
 * {@code gigsynchub.bandzone.client}). Each band connects its own account on the page; {@code login},
 * {@code password} and {@code band-slug} are band #1's from before that, kept once on start
 * ({@link BandzonePlatform}). Secrets never leave this record ({@link #toString} hides them).
 */
@ConfigurationProperties("gigsynchub.bandzone")
public record BandzoneProperties(String baseUrl, String login, String password, String bandSlug, Proxy proxy) {

    /**
     * Where this server's Bandzone traffic comes FROM, when it should not be this machine's own
     * address: {@code gigsynchub.bandzone.proxy.host/port/username/password}. See
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

    public BandzoneProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://bandzone.cz" : baseUrl;
        login = login == null ? "" : login.strip();
        password = password == null ? "" : password;
        bandSlug = bandSlug == null ? "" : bandSlug.strip();
        proxy = proxy == null ? new Proxy(null, null, null, null) : proxy;
    }

    /** Straight out of this machine unless a proxy is configured. */
    public PlatformEgress egress() {
        return proxy.egress();
    }

    @Override
    public String toString() {
        return "BandzoneProperties[baseUrl=" + baseUrl + ", bandSlug=" + bandSlug + ", egress=" + egress()
                + ", login=" + (login.isBlank()
                ? "" : "***") + ", password=" + (password.isBlank() ? "" : "***") + "]";
    }
}
