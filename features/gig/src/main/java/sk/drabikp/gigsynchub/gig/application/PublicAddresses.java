package sk.drabikp.gigsynchub.gig.application;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Whether the server may fetch an address a band gave it (its calendar, a gig's poster): only over http(s),
 * and only when every address its host resolves to is on the public internet — never the server itself, its
 * containers' network (the database, the sign-in, the management port), a private network or a cloud's
 * metadata address. Checked before every request and every redirect (a host could still change its answer in
 * between — the remaining risk of DNS rebinding).
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
