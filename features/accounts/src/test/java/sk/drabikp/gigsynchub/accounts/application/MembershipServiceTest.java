package sk.drabikp.gigsynchub.accounts.application;

import org.junit.jupiter.api.Test;
import sk.drabikp.gigsynchub.accounts.application.port.in.AccountsErrorCode;
import sk.drabikp.gigsynchub.accounts.application.port.in.BandsUseCase.MyBand;
import sk.drabikp.gigsynchub.accounts.application.port.in.InvitationsUseCase.Created;
import sk.drabikp.gigsynchub.accounts.application.port.in.InvitationsUseCase.Offer;
import sk.drabikp.gigsynchub.accounts.domain.AuditEvent;
import sk.drabikp.gigsynchub.accounts.domain.Role;
import sk.drabikp.gigsynchub.accounts.domain.User;
import sk.drabikp.gigsynchub.gig.application.NotFoundException;
import sk.drabikp.gigsynchub.gig.application.UserFacingException;
import sk.drabikp.gigsynchub.gig.domain.BandId;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MembershipServiceTest {

    private final AccountsFakes.SignedIn login = new AccountsFakes.SignedIn();
    private final AccountsFakes.Stores stores = AccountsFakes.Stores.withFirstBand();
    private final AccountsFakes.Invitations invitations = new AccountsFakes.Invitations();
    private Instant now = Instant.parse("2026-10-10T10:00:00Z");
    private final AccountsService accounts = AccountsFakes.service(login, stores, "nobody@x.test");
    private final MembershipService members = new MembershipService(stores, stores, stores, invitations,
            AccountsFakes.DIRECT, new Clock() {
        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    });

    private User signIn(String subject, String email) {
        login.as(subject, email);
        return accounts.current().orElseThrow();
    }

    @Test
    void an_owner_invites_by_address_and_only_who_signs_in_with_it_joins_once() {
        User owner = signIn("sub-1", "owner@band.test");
        MyBand band = accounts.create(owner.id(), "The Band", "the-band");
        User drummer = signIn("sub-2", "Drummer@Band.test");
        User stranger = signIn("sub-3", "stranger@else.test");

        Created created = members.invite(owner.id(), band.band().id(), " drummer@band.test ", Role.MANAGER);

        assertThat(created.token()).hasSizeGreaterThan(40);
        assertThat(invitations.byHash).as("only the token's hash is kept")
                .containsOnlyKeys(MembershipService.hash(created.token()));
        assertThat(members.open(owner.id(), band.band().id())).singleElement()
                .satisfies(i -> assertThat(i.email()).isEqualTo("drummer@band.test"));
        Offer offer = members.offer(stranger.id(), created.token());
        assertThat(offer).isEqualTo(new Offer("The Band", Role.MANAGER, "d***@band.test", true, false, false));
        assertThatThrownBy(() -> members.accept(stranger.id(), created.token()))
                .isInstanceOfSatisfying(UserFacingException.class, e -> {
                    assertThat(e.code()).isEqualTo(AccountsErrorCode.INVITATION_NOT_FOR_YOU);
                    assertThat(e.args()).containsEntry("email", "d***@band.test");
                });

        MyBand joined = members.accept(drummer.id(), created.token());

        assertThat(joined.role()).isEqualTo(Role.MANAGER);
        assertThat(accounts.role(drummer.id(), band.band().id())).contains(Role.MANAGER);
        assertThat(members.open(owner.id(), band.band().id())).isEmpty();
        assertThatThrownBy(() -> members.accept(drummer.id(), created.token()))
                .isInstanceOfSatisfying(UserFacingException.class,
                        e -> assertThat(e.code()).isEqualTo(AccountsErrorCode.INVITATION_GONE));
        assertThatThrownBy(() -> members.offer(drummer.id(), "made-up")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void an_invitation_expires_after_a_week_and_can_be_revoked() {
        User owner = signIn("sub-1", "owner@band.test");
        MyBand band = accounts.create(owner.id(), "The Band", "the-band");
        User drummer = signIn("sub-2", "drummer@band.test");
        Created late = members.invite(owner.id(), band.band().id(), "drummer@band.test", Role.MANAGER);

        now = now.plus(Duration.ofDays(8));

        assertThat(members.offer(drummer.id(), late.token()).open()).isFalse();
        assertThatThrownBy(() -> members.accept(drummer.id(), late.token())).isInstanceOf(UserFacingException.class);
        Created revoked = members.invite(owner.id(), band.band().id(), "drummer@band.test", Role.OWNER);
        members.revoke(owner.id(), band.band().id(), revoked.invitation().id());
        assertThatThrownBy(() -> members.accept(drummer.id(), revoked.token())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void only_owners_invite_and_nobody_twice_or_at_no_address() {
        User owner = signIn("sub-1", "owner@band.test");
        MyBand band = accounts.create(owner.id(), "The Band", "the-band");
        User manager = signIn("sub-2", "manager@band.test");
        members.accept(manager.id(), members.invite(owner.id(), band.band().id(), "manager@band.test",
                Role.MANAGER).token());

        assertThatThrownBy(() -> members.invite(manager.id(), band.band().id(), "x@y.test", Role.MANAGER))
                .isInstanceOfSatisfying(UserFacingException.class,
                        e -> assertThat(e.code()).isEqualTo(AccountsErrorCode.OWNER_ONLY));
        assertThatThrownBy(() -> members.invite(owner.id(), band.band().id(), "Manager@band.test", Role.OWNER))
                .isInstanceOfSatisfying(UserFacingException.class,
                        e -> assertThat(e.code()).isEqualTo(AccountsErrorCode.ALREADY_MEMBER));
        assertThatThrownBy(() -> members.invite(owner.id(), band.band().id(), "not an address", Role.OWNER))
                .isInstanceOfSatisfying(UserFacingException.class,
                        e -> assertThat(e.code()).isEqualTo(AccountsErrorCode.INVALID_EMAIL));
        assertThatThrownBy(() -> members.open(signIn("sub-9", "x@y.test").id(), band.band().id()))
                .as("a stranger doesn't find the band").isInstanceOf(NotFoundException.class);
    }

    @Test
    void a_band_keeps_an_owner_who_hands_it_over_before_leaving() {
        User owner = signIn("sub-1", "owner@band.test");
        MyBand band = accounts.create(owner.id(), "The Band", "the-band");
        BandId id = band.band().id();
        User manager = signIn("sub-2", "manager@band.test");
        members.accept(manager.id(), members.invite(owner.id(), id, "manager@band.test", Role.MANAGER).token());

        assertThatThrownBy(() -> members.remove(owner.id(), id, owner.id()))
                .isInstanceOfSatisfying(UserFacingException.class,
                        e -> assertThat(e.code()).isEqualTo(AccountsErrorCode.LAST_OWNER));
        assertThatThrownBy(() -> members.changeRole(owner.id(), id, owner.id(), Role.MANAGER))
                .isInstanceOf(UserFacingException.class);
        assertThatThrownBy(() -> members.changeRole(manager.id(), id, manager.id(), Role.OWNER))
                .as("a manager can't make themselves an owner").isInstanceOf(UserFacingException.class);

        members.changeRole(owner.id(), id, manager.id(), Role.OWNER);       // handed over
        members.remove(owner.id(), id, owner.id());                          // and left

        assertThat(accounts.role(owner.id(), id)).isEmpty();
        assertThat(accounts.role(manager.id(), id)).contains(Role.OWNER);
        assertThatThrownBy(() -> members.remove(owner.id(), id, manager.id()))
                .as("who left can't remove anyone").isInstanceOf(NotFoundException.class);
    }

    @Test
    void an_owner_deletes_the_band_by_typing_its_address_and_owns_at_most_ten() {
        User owner = signIn("sub-1", "owner@band.test");
        MyBand band = accounts.create(owner.id(), "The Band", "the-band");

        assertThatThrownBy(() -> accounts.delete(owner.id(), band.band().id(), "the-bnd"))
                .isInstanceOfSatisfying(UserFacingException.class,
                        e -> assertThat(e.code()).isEqualTo(AccountsErrorCode.CONFIRM_DELETE));
        accounts.delete(owner.id(), band.band().id(), "the-band");
        assertThat(accounts.myBands(owner.id())).isEmpty();
        assertThat(stores.byId(band.band().id())).isEmpty();

        IntStream.range(0, 10).forEach(i -> accounts.create(owner.id(), "Band " + i, "band-" + i));
        assertThatThrownBy(() -> accounts.create(owner.id(), "One too many", "one-too-many"))
                .isInstanceOfSatisfying(UserFacingException.class, e -> {
                    assertThat(e.code()).isEqualTo(AccountsErrorCode.TOO_MANY_BANDS);
                    assertThat(e.args()).containsEntry("max", "10");
                });
    }

    @Test
    void owners_read_the_audit_log_with_who_did_what() {
        User owner = signIn("sub-1", "owner@band.test");
        MyBand band = accounts.create(owner.id(), "The Band", "the-band");
        User manager = signIn("sub-2", "manager@band.test");
        members.accept(manager.id(), members.invite(owner.id(), band.band().id(), "manager@band.test",
                Role.MANAGER).token());
        AccountsFakes.Audit audit = new AccountsFakes.Audit();
        AuditService log = new AuditService(audit, stores, stores);

        log.record(new AuditEvent(band.band().id(), manager.id(), "updateGig", "2026-11-14_klub", 200, now));
        log.record(new AuditEvent(BandId.FIRST, manager.id(), "updateGig", "elsewhere", 200, now));

        assertThat(log.recent(owner.id(), band.band().id(), 50)).singleElement().satisfies(e -> {
            assertThat(e.who()).isEqualTo("manager@band.test");
            assertThat(e.event().action()).isEqualTo("updateGig");
        });
        assertThatThrownBy(() -> log.recent(manager.id(), band.band().id(), 50))
                .isInstanceOfSatisfying(UserFacingException.class,
                        e -> assertThat(e.code()).isEqualTo(AccountsErrorCode.OWNER_ONLY));
    }

    @Test
    void deleting_ones_account_needs_another_owner_where_others_stay_and_takes_bands_one_is_alone_in() {
        User owner = signIn("sub-1", "owner@band.test");
        MyBand shared = accounts.create(owner.id(), "Shared", "shared");
        MyBand alone = accounts.create(owner.id(), "Alone", "alone");
        User manager = signIn("sub-2", "manager@band.test");
        members.accept(manager.id(), members.invite(owner.id(), shared.band().id(), "manager@band.test",
                Role.MANAGER).token());

        assertThatThrownBy(() -> accounts.deleteMe(owner.id()))
                .isInstanceOfSatisfying(UserFacingException.class, e -> {
                    assertThat(e.code()).isEqualTo(AccountsErrorCode.LAST_OWNER);
                    assertThat(e.args()).containsEntry("band", "Shared");
                });
        members.changeRole(owner.id(), shared.band().id(), manager.id(), Role.OWNER);

        accounts.deleteMe(owner.id());

        assertThat(stores.byId(owner.id())).isEmpty();
        assertThat(stores.byId(alone.band().id())).as("the band only they were in").isEmpty();
        assertThat(stores.byId(shared.band().id())).isPresent();
        assertThat(accounts.role(manager.id(), shared.band().id())).contains(Role.OWNER);
    }
}
