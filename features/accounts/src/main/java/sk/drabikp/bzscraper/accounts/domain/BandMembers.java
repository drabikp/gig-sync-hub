package sk.drabikp.bzscraper.accounts.domain;

import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;
import sk.drabikp.bzscraper.gig.domain.BandId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Everyone who manages one band — its roster. ONE value rather than a list its callers scan, because
 * the band's rule (it keeps at least one owner) is about all of them together: the rule is asked of
 * the roster as a change WOULD leave it — {@code members.without(user).hasOwner()} — in one place,
 * instead of being written out again at each of the four places that can break it (changing a role,
 * removing a member, leaving, deleting an account).
 *
 * <p>Immutable, as a {@link Membership} is: {@link #without} and {@link #as} answer another roster
 * rather than changing this one, so a caller may ask whether a change is allowed before making it.
 * The rule is only as good as the roster it was asked of, so whoever is about to change it holds it
 * first ({@code MembershipStore.hold}) — otherwise two owners leaving at the same moment each see
 * the other.
 */
@AggregateRoot
public record BandMembers(@Identity BandId band, List<Membership> members) {

    public BandMembers {
        Objects.requireNonNull(band, "the band");
        List<Membership> all = members == null ? List.of() : List.copyOf(members);
        if (all.stream().anyMatch(m -> !m.band().equals(band))) {
            throw new IllegalArgumentException("another band's membership among " + band + "'s members");
        }
        members = all;
    }

    /** Nobody manages the band. */
    public static BandMembers none(BandId band) {
        return new BandMembers(band, List.of());
    }

    /** Their membership, if they manage the band. */
    public Optional<Membership> of(UserId user) {
        return members.stream().filter(m -> m.user().equals(user)).findFirst();
    }

    /** Somebody owns the band — what every change to the roster is checked against. */
    public boolean hasOwner() {
        return members.stream().anyMatch(m -> m.role() == Role.OWNER);
    }

    /** They are the band's only member — nobody would be left to manage it. */
    public boolean onlyMember(UserId user) {
        return members.size() == 1 && of(user).isPresent();
    }

    public int size() {
        return members.size();
    }

    /** The roster without them — what leaving, being removed, or deleting an account would leave. */
    public BandMembers without(UserId user) {
        return new BandMembers(band, members.stream().filter(m -> !m.user().equals(user)).toList());
    }

    /**
     * The roster with them in that role — what a role change would leave. Keeps the name they go by in
     * the band; somebody who wasn't a member is added (as accepting an invitation adds them).
     */
    public BandMembers as(UserId user, Role role) {
        Objects.requireNonNull(role, "the member's role");
        String displayName = of(user).map(Membership::displayName).orElse(null);
        List<Membership> changed = new ArrayList<>(without(user).members());
        changed.add(new Membership(user, band, role, displayName));
        return new BandMembers(band, changed);
    }
}
