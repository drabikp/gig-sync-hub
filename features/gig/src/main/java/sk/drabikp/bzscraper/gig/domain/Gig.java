package sk.drabikp.bzscraper.gig.domain;

import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Aggregate root: a gig to publish. Always valid — the compact constructor enforces
 * the invariants (title, schedule, location and admission are required), so an
 * invalid {@code Gig} cannot exist and downstream code needs no defensive checks.
 *
 * Composed of value objects ({@link GigSchedule}, {@link Location}, {@link Admission});
 * identified by {@link GigId}, which is the gig's own and survives every edit — a gig moved to
 * another day or venue is the same gig, and only {@link #match()} (the day and the place) changes
 * with it. Immutable: state-changing behaviour ({@link #rescheduledTo}, {@link #cancel}) returns a
 * new instance, with the same identity. Descriptive attributes (description, links, poster) are
 * optional and nullable.
 */
@AggregateRoot
public record Gig(
        @Identity GigId id,
        String title,
        GigSchedule schedule,
        Location location,
        List<String> lineup,
        Admission admission,
        String description,
        String facebookUrl,
        String ticketUrl,
        String posterImageUrl,
        boolean cancelled
) {
    public Gig {
        if (id == null) {
            throw new IllegalArgumentException("a gig's identity is required");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("gig title is required");
        }
        if (schedule == null) {
            throw new IllegalArgumentException("gig schedule is required");
        }
        if (location == null) {
            throw new IllegalArgumentException("gig location is required");
        }
        if (admission == null) {
            throw new IllegalArgumentException("gig admission is required");
        }
        title = title.trim();
        lineup = lineup == null ? List.of() : List.copyOf(lineup);
    }

    /** Factory for a fresh (not cancelled) gig: a new gig, with an identity no other gig has. */
    public static Gig create(String title, GigSchedule schedule, Location location, List<String> lineup,
                             Admission admission, String description, String facebookUrl,
                             String ticketUrl, String posterImageUrl) {
        return create(GigId.fresh(), title, schedule, location, lineup, admission, description, facebookUrl,
                ticketUrl, posterImageUrl);
    }

    /**
     * Factory for the gig that already has this identity (not cancelled) — an edit of it, or the
     * gig a stored row holds.
     */
    public static Gig create(GigId id, String title, GigSchedule schedule, Location location, List<String> lineup,
                             Admission admission, String description, String facebookUrl,
                             String ticketUrl, String posterImageUrl) {
        return new Gig(id, title, schedule, location, lineup, admission, description, facebookUrl,
                ticketUrl, posterImageUrl, false);
    }

    /**
     * Which concert this is — the day and the place, as every source of a copy of it writes them.
     * Not the gig's identity ({@link #id()}): this changes when the gig moves, and a band may have
     * only one gig per match.
     */
    public GigMatch match() {
        return GigMatch.of(this);
    }

    /**
     * The words a band would look for this gig by — its title, venue and town — folded the way
     * everything compared across sources is ({@link TextFold}). The store keeps this with the row and
     * searches it there, so the one rule lives here rather than in each store that has to agree with it.
     */
    public String searchText() {
        String venue = location.venue() == null ? "" : location.venue();
        return TextFold.fold(title + " " + venue + " " + location.city());
    }

    /** The band's show day ({@link GigSchedule#showStart}) is before today, in the gig's own time zone. */
    public boolean isPast(Clock clock) {
        ZonedDateTime show = schedule.showStart();
        return show.toLocalDate().isBefore(LocalDate.now(clock.withZone(show.getZone())));
    }

    public Gig rescheduledTo(GigSchedule newSchedule) {
        return new Gig(id, title, newSchedule, location, lineup, admission, description, facebookUrl,
                ticketUrl, posterImageUrl, cancelled);
    }

    public Gig cancel() {
        return new Gig(id, title, schedule, location, lineup, admission, description, facebookUrl,
                ticketUrl, posterImageUrl, true);
    }

    /**
     * Everything the gig says as one text, in a fixed order the gig owns (not {@code toString},
     * whose form is unspecified): equal gigs, equal texts — what a revision is made from.
     */
    public String fingerprint() {
        Slot slot = schedule.slot();
        Address address = location.address() != null ? location.address() : Address.NONE;
        return Stream.of(title, schedule.start(), schedule.end(), slot == null ? null : slot.start(),
                        slot == null ? null : slot.end(), location.venue(), location.city(), location.country(),
                        address.street(), address.postalCode(), address.district(), address.region(),
                        address.latitude(), address.longitude(), String.join("\n", lineup), admission.type(),
                        admission.amount(), description, facebookUrl, ticketUrl, posterImageUrl, cancelled)
                .map(String::valueOf).collect(Collectors.joining("\u001f"));
    }

    public Gig reactivate() {
        return new Gig(id, title, schedule, location, lineup, admission, description, facebookUrl,
                ticketUrl, posterImageUrl, false);
    }
}
