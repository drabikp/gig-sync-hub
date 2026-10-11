package sk.drabikp.bzscraper.calendar.domain;

import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;
import sk.drabikp.bzscraper.gig.domain.BandId;

import java.net.URI;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Objects;

/**
 * Where a band's calendar is read from: its private iCal address — a secret, it lets anyone read the
 * whole calendar ({@link #toString} hides it; only its {@link #host} is ever shown) — and the zone its
 * times without one are in. {@code webcal://} addresses (as calendars hand them out) are read over
 * {@code https://}.
 */
@AggregateRoot
public record BandCalendar(@Identity BandId band, String address, ZoneId zone) {

    public BandCalendar {
        Objects.requireNonNull(band, "the band");
        Objects.requireNonNull(zone, "the calendar's zone");
        if (!validAddress(address)) {
            throw new IllegalArgumentException("not a calendar address");
        }
        address = address.strip().replaceFirst("(?i)^webcal://", "https://");
    }

    /** An http(s) or webcal address with a host. */
    public static boolean validAddress(String address) {
        if (address == null || address.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(address.strip());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            return (scheme.equals("https") || scheme.equals("http") || scheme.equals("webcal"))
                    && uri.getHost() != null && !uri.getHost().isBlank();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Whose calendar it is, without the secret part: {@code calendar.google.com}. */
    public String host() {
        return URI.create(address).getHost();
    }

    @Override
    public String toString() {
        return "BandCalendar[band=" + band + ", host=" + host() + ", zone=" + zone + "]";
    }
}
