package sk.drabikp.gigsynchub.gig.application;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.security.Security;
import java.util.Locale;

/**
 * Whether the server may fetch an address a band gave it (its calendar, a gig's poster): only over http(s),
 * and only when every address its host resolves to is on the public internet — never the server itself, its
 * containers' network (the database, the sign-in, the management port), a private network or a cloud's
 * metadata address. Checked before every request and every redirect.
 *
 * <p>And what was vetted is what is then connected to — see {@link #holdResolvedNamesStill()},
 * which closes the gap between this check's lookup and the HTTP client's own (DNS rebinding, which
 * this class used to name as its remaining risk).
 */
public final class PublicAddresses {

    private PublicAddresses() {
    }

    /** The address, when the server may fetch it; {@link IllegalArgumentException} saying why not. */
    public static URI checked(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("only http(s) addresses are fetched");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("the address names no host");
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("the host " + host + " is unknown");
        }
        for (InetAddress address : addresses) {
            if (!isPublic(address)) {
                throw new IllegalArgumentException("the host " + host + " isn't on the public internet");
            }
        }
        return uri;
    }

    /**
     * The address this check resolved is the address the socket then goes to.
     *
     * <p>There are TWO lookups of a band's host — this class's, which decides whether the address is
     * on the public internet, and the HTTP client's own a moment later, which decides where the
     * socket goes — and nothing in the JDK says they agree. A host whose name server answers
     * differently each time (DNS rebinding) passes the first with a public address and serves the
     * second a private one: the database, the sign-in, the management port on 8081.
     *
     * <p>What makes them agree is the platform's own cache of successful lookups, which sits ABOVE
     * every resolver: the second lookup is answered out of what the first one got. That is already
     * the behaviour by default, and this is the line that stops it being a default — a
     * {@code java.security} with {@code networkaddress.cache.ttl=0} (some hardened images ship one)
     * would turn the guarantee off with nothing to show for it. Thirty seconds is a fetch and the
     * redirects it is allowed, not a cache anybody relies on.
     *
     * <p><b>Called from the app's own class initializer, and it has to be that early</b>: the JDK
     * reads this property ONCE, when its cache policy is first used, which is the first name any
     * part of the process resolves. Setting it after that changes nothing.
     *
     * <p>The reviewer's suggestion was the other way round — resolve once here and hand the answer
     * to the connection, which the JDK does offer as {@code InetAddressResolverProvider} (JEP 418).
     * That was built and taken out again: the platform looks for such a provider on the SYSTEM class
     * path, and a Spring Boot executable jar keeps the application's classes and its
     * {@code META-INF/services} inside {@code BOOT-INF}, where the platform never looks (Spring Boot
     * puts its OWN service file at the jar's root for exactly this reason). It passed every test and
     * would have done nothing in production, which is worse than not having it.
     */
    public static void holdResolvedNamesStill() {
        Security.setProperty("networkaddress.cache.ttl", "30");
        Security.setProperty("networkaddress.cache.negative.ttl", "10");
    }

    /** The most a band's address may answer with (a calendar, a poster). */
    public static final int MAX_BODY_BYTES = 10 * 1024 * 1024;

    /**
     * Reads at most {@link #MAX_BODY_BYTES}; {@link IllegalArgumentException} for more — a band's address
     * can't fill the server's memory or disk.
     */
    public static byte[] limited(InputStream body) throws IOException {
        try (body) {
            byte[] read = body.readNBytes(MAX_BODY_BYTES + 1);
            if (read.length > MAX_BODY_BYTES) {
                throw new IllegalArgumentException("the answer is larger than " + MAX_BODY_BYTES / 1024 / 1024 + " MB");
            }
            return read;
        }
    }

    /** Whether the address may be fetched — false for an address that isn't one. */
    public static boolean fetchable(String address) {
        try {
            checked(URI.create(address.strip()));
            return true;
        } catch (IllegalArgumentException | NullPointerException e) {
            return false;
        }
    }

    static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] b = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = b[0] & 0xff;
            int second = b[1] & 0xff;
            return first != 0                                           // "this network"
                    && !(first == 100 && second >= 64 && second <= 127)  // carrier-grade NAT
                    && !(first == 192 && second == 0 && (b[2] & 0xff) == 0)
                    && !(first == 198 && (second == 18 || second == 19)) // benchmarking
                    && first < 240;                                       // reserved, broadcast
        }
        if (address instanceof Inet6Address) {
            int first = b[0] & 0xff;
            if ((first & 0xfe) == 0xfc) {
                return false;                                            // unique local fc00::/7
            }
            if (first == 0x20 && (b[1] & 0xff) == 0x02) {
                return false;                                            // 6to4 2002::/16 (an IPv4 inside)
            }
            boolean nat64 = (first == 0x00 && (b[1] & 0xff) == 0x64 && (b[2] & 0xff) == 0xff && (b[3] & 0xff) == 0x9b);
            if (nat64) {
                return false;                                            // NAT64 64:ff9b::/96 (an IPv4 inside)
            }
            boolean mapped = true;                                       // ::ffff:a.b.c.d — the IPv4 address's rules
            for (int i = 0; i < 10; i++) {
                mapped &= b[i] == 0;
            }
            if (mapped && (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff) {
                try {
                    return isPublic(InetAddress.getByAddress(new byte[] {b[12], b[13], b[14], b[15]}));
                } catch (UnknownHostException e) {
                    return false;
                }
            }
        }
        return true;
    }
}
