package sk.drabikp.bzscraper.accounts.application;

import sk.drabikp.bzscraper.accounts.application.port.in.AccountsErrorCode;
import sk.drabikp.bzscraper.accounts.application.port.in.BandsUseCase.MyBand;
import sk.drabikp.bzscraper.accounts.application.port.in.InvitationsUseCase;
import sk.drabikp.bzscraper.accounts.application.port.in.MembersUseCase;
import sk.drabikp.bzscraper.accounts.application.port.out.BandStore;
import sk.drabikp.bzscraper.accounts.application.port.out.InvitationStore;
import sk.drabikp.bzscraper.accounts.application.port.out.MembershipStore;
import sk.drabikp.bzscraper.accounts.application.port.out.UserStore;
import sk.drabikp.bzscraper.accounts.domain.Band;
import sk.drabikp.bzscraper.accounts.domain.BandMembers;
import sk.drabikp.bzscraper.accounts.domain.Invitation;
import sk.drabikp.bzscraper.accounts.domain.Membership;
import sk.drabikp.bzscraper.accounts.domain.Role;
import sk.drabikp.bzscraper.accounts.domain.User;
import sk.drabikp.bzscraper.accounts.domain.UserId;
import sk.drabikp.bzscraper.gig.application.NotFoundException;
import sk.drabikp.bzscraper.gig.application.UserFacingException;
import sk.drabikp.bzscraper.gig.application.port.out.Transactions;
import sk.drabikp.bzscraper.gig.domain.BandId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A band's members and the invitations that bring new ones. Owners invite, change roles and remove; anyone
 * may leave; a band always keeps an owner. An invitation's link carries a random token (256 bits) — only its
 * SHA-256 is kept, so the database alone can't be used to join a band.
 */
public class MembershipService implements MembersUseCase, InvitationsUseCase {

    private final UserStore users;
    private final BandStore bands;
    private final MembershipStore memberships;
    private final InvitationStore invitations;
    private final Transactions transactions;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public MembershipService(UserStore users, BandStore bands, MembershipStore memberships,
                             InvitationStore invitations, Transactions transactions, Clock clock) {
        this.users = users;
        this.bands = bands;
        this.memberships = memberships;
        this.invitations = invitations;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public List<Member> members(UserId asker, BandId band) {
        member(asker, band);
        return memberships.ofBand(band).members().stream()
                .flatMap(m -> users.byId(m.user()).map(u -> new Member(u, m.role(), m.displayName())).stream())
                .sorted(Comparator.comparing((Member m) -> m.role() != Role.OWNER)
                        .thenComparing(m -> String.valueOf(m.user().shownAs())))
                .toList();
    }

    @Override
    public Member changeRole(UserId owner, BandId band, UserId member, Role role) {
        return transactions.computeInTransaction(() -> {
            owner(owner, band);
            BandMembers roster = heldRoster(band);
            Membership theirs = roster.of(member).orElseThrow(() -> new NotFoundException("No such member."));
            requireAnOwnerIn(roster.as(member, role));
            Membership changed = new Membership(member, band, role, theirs.displayName());
            memberships.save(changed);
            User user = users.byId(member).orElseThrow(() -> new NotFoundException("No such member."));
            return new Member(user, role, changed.displayName());
        });
    }

    @Override
    public void remove(UserId asker, BandId band, UserId member) {
        transactions.inTransaction(() -> {
            if (!asker.equals(member)) {
                owner(asker, band);
            }
            BandMembers roster = heldRoster(band);
            roster.of(member).orElseThrow(() -> new NotFoundException("No such member."));
            requireAnOwnerIn(roster.without(member));
            memberships.delete(member, band);
        });
    }

    @Override
    public Created invite(UserId owner, BandId band, String email, Role role) {
        return transactions.computeInTransaction(() -> {
            owner(owner, band);
            if (!Invitation.validEmail(email)) {
                throw new UserFacingException(AccountsErrorCode.INVALID_EMAIL, Map.of("email", String.valueOf(email)),
                        "That isn't an e-mail address.");
            }
            String address = Invitation.normalized(email);
            boolean member = memberships.ofBand(band).members().stream()
                    .flatMap(m -> users.byId(m.user()).stream())
                    .anyMatch(u -> u.email() != null && Invitation.normalized(u.email()).equals(address));
            if (member) {
                throw new UserFacingException(AccountsErrorCode.ALREADY_MEMBER, Map.of("email", address),
                        address + " is the band's member already.");
            }
            Instant now = clock.instant();
            invitations.open(band, now).stream().filter(i -> i.email().equals(address))
                    .forEach(i -> invitations.delete(i.id()));
            String token = newToken();
            Invitation invitation = new Invitation(UUID.randomUUID(), band, address, role, owner, now,
                    now.plus(Duration.ofDays(VALID_DAYS)), null);
            invitations.save(invitation, hash(token));
            return new Created(invitation, token);
        });
    }

    @Override
    public List<Invitation> open(UserId member, BandId band) {
        member(member, band);
        return invitations.open(band, clock.instant());
    }

    @Override
    public void revoke(UserId owner, BandId band, UUID invitation) {
        transactions.inTransaction(() -> {
            owner(owner, band);
            invitations.of(band, invitation).orElseThrow(() -> new NotFoundException("No such invitation."));
            invitations.delete(invitation);
        });
    }

    @Override
    public Offer offer(UserId user, String token) {
        Invitation invitation = byToken(token);
        User signedIn = users.byId(user).orElseThrow(() -> new NotFoundException("No such user."));
        String bandName = bands.byId(invitation.band()).map(Band::name).orElseThrow(() ->
                new NotFoundException("No such invitation."));
        return new Offer(bandName, invitation.role(), invitation.maskedEmail(), invitation.open(clock.instant()),
                invitation.isFor(signedIn.email()), memberships.of(user, invitation.band()).isPresent());
    }

    @Override
    public MyBand accept(UserId user, String token) {
        return transactions.computeInTransaction(() -> {
            Invitation invitation = byToken(token);
            Instant now = clock.instant();
            if (!invitation.open(now)) {
                throw new UserFacingException(AccountsErrorCode.INVITATION_GONE, Map.of(),
                        "This invitation was used already, or it has expired — ask for a new one.");
            }
            User signedIn = users.byId(user).orElseThrow(() -> new NotFoundException("No such user."));
            if (!invitation.isFor(signedIn.email())) {
                throw new UserFacingException(AccountsErrorCode.INVITATION_NOT_FOR_YOU,
                        Map.of("email", invitation.maskedEmail()),
                        "This invitation is for " + invitation.maskedEmail() + " — sign in with that address.");
            }
            Band band = bands.byId(invitation.band()).orElseThrow(() -> new NotFoundException("No such band."));
            Membership membership = memberships.of(user, band.id())
                    .orElseGet(() -> new Membership(user, band.id(), invitation.role(), null));
            memberships.save(membership);
            invitations.save(invitation.accepted(now));
            return new MyBand(band, membership.role(), membership.displayName());
        });
    }

    private Invitation byToken(String token) {
        if (token == null || token.isBlank() || token.length() > 100) {
            throw new NotFoundException("No such invitation.");
        }
        return invitations.byTokenHash(hash(token)).orElseThrow(() -> new NotFoundException("No such invitation."));
    }

    /**
     * The band's roster, held for the rest of the transaction first: whoever is about to change it
     * decides on the roster nobody else is changing meanwhile.
     */
    private BandMembers heldRoster(BandId band) {
        memberships.hold(band);
        return memberships.ofBand(band);
    }

    /** The one rule, asked of the roster the change would leave behind. */
    private static void requireAnOwnerIn(BandMembers afterwards) {
        if (!afterwards.hasOwner()) {
            throw new UserFacingException(AccountsErrorCode.LAST_OWNER, Map.of(),
                    "A band needs an owner — make another member an owner first.");
        }
    }

    private Role member(UserId user, BandId band) {
        return memberships.of(user, band).map(Membership::role)
                .orElseThrow(() -> new NotFoundException("No such band."));
    }

    private void owner(UserId user, BandId band) {
        if (!member(user, band).mayChangeTheBand()) {
            throw new UserFacingException(AccountsErrorCode.OWNER_ONLY, Map.of(), "Only the band's owners may do that.");
        }
    }

    private String newToken() {
        byte[] token = new byte[32];
        random.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
