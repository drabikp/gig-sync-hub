package sk.drabikp.gigsynchub.accounts.adapter.out.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import sk.drabikp.gigsynchub.accounts.domain.AuditEvent;
import sk.drabikp.gigsynchub.accounts.domain.Band;
import sk.drabikp.gigsynchub.accounts.domain.Invitation;
import sk.drabikp.gigsynchub.accounts.domain.Membership;
import sk.drabikp.gigsynchub.accounts.domain.Role;
import sk.drabikp.gigsynchub.accounts.domain.User;
import sk.drabikp.gigsynchub.accounts.domain.UserId;
import sk.drabikp.gigsynchub.gig.domain.BandId;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class JpaAccountsStoresTest {

    @Autowired
    private JpaAccountsStores stores;

    @Test
    void the_first_band_is_there_after_the_migrations() {
        assertThat(stores.byId(BandId.FIRST)).isPresent();
    }

    @Test
    void users_bands_and_memberships_round_trip() {
        User user = new User(new UserId(UUID.randomUUID()), "sub-1", "a@band.test", "Ann");
        Band band = new Band(new BandId(UUID.randomUUID()), "the-band", "The Band");
        stores.save(user);
        stores.save(band);
        stores.save(new Membership(user.id(), band.id(), Role.OWNER, "Ann B."));

        assertThat(stores.bySubject("sub-1")).contains(user);
        assertThat(stores.bySlug("the-band")).contains(band);
        assertThat(stores.ofUser(user.id())).singleElement().satisfies(m -> {
            assertThat(m.band()).isEqualTo(band.id());
            assertThat(m.role()).isEqualTo(Role.OWNER);
            assertThat(m.displayName()).isEqualTo("Ann B.");
        });
        assertThat(stores.ofBand(band.id()).members()).hasSize(1);
        assertThat(stores.of(user.id(), BandId.FIRST)).isEmpty();
    }

    @Test
    void invitations_are_found_by_their_tokens_hash_and_deleting_a_band_takes_everything_of_it() {
        User owner = new User(new UserId(UUID.randomUUID()), "sub-9", "o@band.test", "O");
        Band band = new Band(new BandId(UUID.randomUUID()), "to-delete", "To delete");
        stores.save(owner);
        stores.save(band);
        stores.save(new Membership(owner.id(), band.id(), Role.OWNER, null));
        Instant now = Instant.parse("2026-10-10T10:00:00Z");
        Invitation invitation = new Invitation(UUID.randomUUID(), band.id(), "x@band.test", Role.MANAGER, owner.id(),
                now, now.plusSeconds(3600), null);
        stores.save(invitation, "a".repeat(64));
        stores.add(new AuditEvent(band.id(), owner.id(), "invite", null, 201, now));

        assertThat(stores.byTokenHash("a".repeat(64))).contains(invitation);
        assertThat(stores.open(band.id(), now)).containsExactly(invitation);
        assertThat(stores.open(band.id(), now.plusSeconds(7200))).as("expired").isEmpty();
        stores.save(invitation.accepted(now));
        assertThat(stores.open(band.id(), now)).as("accepted").isEmpty();
        assertThat(stores.recent(band.id(), 10)).extracting(AuditEvent::action).containsExactly("invite");

        stores.delete(band.id());

        assertThat(stores.byId(band.id())).isEmpty();
        assertThat(stores.ofUser(owner.id())).isEmpty();
        assertThat(stores.byTokenHash("a".repeat(64))).isEmpty();
        assertThat(stores.recent(band.id(), 10)).isEmpty();
    }

    /**
     * The audit log is a band's own: who changed what, read by its owners. It is the one table of this
     * module that holds a band's DATA rather than who belongs to which band, so it is the one that has
     * to be shown apart — a log that leaked would say what another band did and when.
     */
    @Test
    void the_audit_log_is_one_bands_and_shows_nothing_of_the_other() {
        Band mine = new Band(new BandId(UUID.randomUUID()), "mine", "Mine");
        Band theirs = new Band(new BandId(UUID.randomUUID()), "theirs", "Theirs");
        stores.save(mine);
        stores.save(theirs);
        User mineOwner = new User(new UserId(UUID.randomUUID()), "sub-mine", "m@band.test", "M");
        User theirsOwner = new User(new UserId(UUID.randomUUID()), "sub-theirs", "t@band.test", "T");
        stores.save(mineOwner);
        stores.save(theirsOwner);
        UserId me = mineOwner.id();
        UserId them = theirsOwner.id();
        Instant now = Instant.parse("2026-10-12T09:00:00Z");

        stores.add(new AuditEvent(mine.id(), me, "gig.delete", "Klub 007", 200, now));
        stores.add(new AuditEvent(theirs.id(), them, "band.rename", "Theirs", 200, now.plusSeconds(1)));

        assertThat(stores.recent(mine.id(), 10)).extracting(AuditEvent::action).containsExactly("gig.delete");
        assertThat(stores.recent(theirs.id(), 10)).extracting(AuditEvent::action).containsExactly("band.rename");

        stores.delete(theirs.id());

        assertThat(stores.recent(mine.id(), 10))
                .as("deleting a band takes its log and nobody else's").hasSize(1);
    }
}
