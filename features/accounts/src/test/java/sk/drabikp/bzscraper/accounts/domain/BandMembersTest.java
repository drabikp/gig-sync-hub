package sk.drabikp.bzscraper.accounts.domain;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.gig.domain.BandId;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The band's rule, asked of the roster a change would leave behind. */
class BandMembersTest {

    private static final BandId BAND = new BandId(UUID.randomUUID());
    private static final BandId OTHER = new BandId(UUID.randomUUID());
    private static final UserId ANN = new UserId(UUID.randomUUID());
    private static final UserId BEN = new UserId(UUID.randomUUID());

    private static Membership member(UserId user, Role role) {
        return new Membership(user, BAND, role, null);
    }

    private static BandMembers roster(Membership... members) {
        return new BandMembers(BAND, List.of(members));
    }

    @Test
    void a_band_with_an_owner_has_one_and_an_empty_roster_has_none() {
        assertThat(roster(member(ANN, Role.OWNER)).hasOwner()).isTrue();
        assertThat(roster(member(ANN, Role.MANAGER)).hasOwner()).isFalse();
        assertThat(BandMembers.none(BAND).hasOwner()).isFalse();
        assertThat(BandMembers.none(BAND).members()).isEmpty();
    }

    @Test
    void the_only_owner_leaving_would_leave_the_band_with_none() {
        BandMembers one = roster(member(ANN, Role.OWNER), member(BEN, Role.MANAGER));

        assertThat(one.without(ANN).hasOwner()).as("the only owner").isFalse();
        assertThat(one.without(BEN).hasOwner()).as("a manager").isTrue();
    }

    @Test
    void the_only_owner_stepping_down_would_too_but_a_second_owner_may() {
        BandMembers one = roster(member(ANN, Role.OWNER), member(BEN, Role.MANAGER));
        BandMembers two = roster(member(ANN, Role.OWNER), member(BEN, Role.OWNER));

        assertThat(one.as(ANN, Role.MANAGER).hasOwner()).isFalse();
        assertThat(two.as(ANN, Role.MANAGER).hasOwner()).isTrue();
        assertThat(one.as(BEN, Role.OWNER).without(ANN).hasOwner()).as("handed over first").isTrue();
    }

    @Test
    void a_role_change_keeps_the_name_they_go_by_and_takes_in_somebody_new() {
        BandMembers named = roster(new Membership(ANN, BAND, Role.MANAGER, "Ann B."));

        assertThat(named.as(ANN, Role.OWNER).of(ANN)).get()
                .extracting(Membership::role, Membership::displayName)
                .containsExactly(Role.OWNER, "Ann B.");
        assertThat(named.as(BEN, Role.MANAGER).size()).isEqualTo(2);
    }

    @Test
    void the_band_knows_whether_somebody_is_alone_in_it() {
        assertThat(roster(member(ANN, Role.OWNER)).onlyMember(ANN)).isTrue();
        assertThat(roster(member(ANN, Role.OWNER)).onlyMember(BEN)).isFalse();
        assertThat(roster(member(ANN, Role.OWNER), member(BEN, Role.MANAGER)).onlyMember(ANN)).isFalse();
    }

    @Test
    void a_roster_is_one_bands_and_answers_the_same_however_it_was_built() {
        assertThatThrownBy(() -> new BandMembers(BAND, List.of(new Membership(ANN, OTHER, Role.OWNER, null))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(roster(member(ANN, Role.OWNER)).of(BEN)).isEmpty();
        assertThat(new BandMembers(BAND, null).members()).isEmpty();
    }
}
