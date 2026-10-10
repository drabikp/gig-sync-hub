package sk.drabikp.bzscraper.catalog.adapter.in.rest;

import sk.drabikp.bzscraper.gig.application.ConcurrentChangeException;
import sk.drabikp.bzscraper.gig.domain.Gig;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * A gig's revision as the API names it: a digest of everything the gig says ({@link Gig#fingerprint()}). An edit names the
 * revision it was made on, so a gig changed meanwhile (another window, an import, the calendar)
 * is not overwritten.
 */
final class GigRevisions {

    private GigRevisions() {
    }

    static String of(Gig gig) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(gig.fingerprint().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The gig as the user saw it at {@code rev}; changed since → nothing is changed, they reload. */
    static Gig asSeen(Gig current, String rev) {
        if (!of(current).equals(rev)) {
            throw ConcurrentChangeException.changed();
        }
        return current;
    }
}
