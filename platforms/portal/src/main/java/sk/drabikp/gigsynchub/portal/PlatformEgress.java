package sk.drabikp.gigsynchub.portal;

import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.http.HttpClient;

/**
 * Where this server's requests to a platform come FROM.
 *
 * <p>A band's account on a platform is normally used from one home, one office, one phone. This
 * server is a fourth place, and whether that looks ordinary or suspicious is mostly about WHERE it
 * is: a datacenter in another country talking to a Czech site, on an account that has only ever
 * been used from Czechia, is the thing worth fixing. So a platform's traffic can be sent through a
 * proxy the operator chooses — their own machine, a paid static address, an address in the band's
 * own country.
 *
 * <p><b>One address, kept.</b> Not a rotating list, and deliberately not: these are the band's own
 * long-lived sessions (Bandzone's lasts years, Bandsintown's is renewed from a kept token), and a
 * session that arrives from a different address every time is not a quieter account — it is the
 * exact shape of a stolen one, and it is what account-takeover detection is built to catch. A
 * public proxy list makes that worse twice over: the addresses carry other people's reputation,
 * much of it already blocked, and the operator of a free proxy is an unknown party in the middle
 * of a band's live session, which is the one thing every other line of this app is written to
 * prevent.
 *
 * <p>Two things the JDK will do to you here, both found by trying it:
 * <ul>
 *   <li>{@code java.net.http} speaks to HTTP proxies only. A SOCKS proxy needs a local bridge, or
 *       the container's own networking.</li>
 *   <li>Basic authentication to a proxy is DISABLED by the JDK for tunnelled (https) requests
 *       unless {@code jdk.http.auth.tunneling.disabledSchemes} is cleared — which this does, for
 *       this app's clients only, when a proxy with a password is configured. Without it the proxy
 *       answers 407 and the client reports something unhelpful about the tunnel.</li>
 * </ul>
 */
public record PlatformEgress(String host, int port, String username, String password) {

    /** Straight out of this machine, which is the default and is right for most people. */
    public static final PlatformEgress DIRECT = new PlatformEgress(null, 0, null, null);

    public PlatformEgress {
        host = host == null || host.isBlank() ? null : host.strip();
        username = username == null || username.isBlank() ? null : username.strip();
        password = password == null || password.isBlank() ? null : password;
        if (host != null && (port < 1 || port > 65535)) {
            throw new IllegalArgumentException("a proxy needs a port between 1 and 65535, not " + port);
        }
    }

    public boolean configured() {
        return host != null;
    }

    /** The builder, with the proxy on it when there is one. */
    public HttpClient.Builder on(HttpClient.Builder builder) {
        if (!configured()) {
            return builder;
        }
        builder.proxy(ProxySelector.of(new InetSocketAddress(host, port)));
        if (username != null) {
            allowBasicAuthenticationThroughATunnel();
            builder.authenticator(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return getRequestorType() == RequestorType.PROXY
                            ? new PasswordAuthentication(username, password.toCharArray())
                            : null;                       // never the platform's own challenge
                }
            });
        }
        return builder;
    }

    /**
     * The JDK refuses Basic to a proxy on a CONNECT tunnel by default (it is sent before TLS, so
     * it is sent in the clear to the proxy — which is the proxy's whole job here, and why this is
     * a setting rather than a rule). Set once, and only when a proxy with a password is actually
     * configured.
     */
    private static void allowBasicAuthenticationThroughATunnel() {
        String disabled = System.getProperty("jdk.http.auth.tunneling.disabledSchemes", "basic");
        if (disabled.toLowerCase(java.util.Locale.ROOT).contains("basic")) {
            System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");
        }
    }

    @Override
    public String toString() {
        return configured()
                ? "PlatformEgress[" + host + ":" + port + (username == null ? "" : ", as " + username + " ***") + "]"
                : "PlatformEgress[direct]";
    }
}
