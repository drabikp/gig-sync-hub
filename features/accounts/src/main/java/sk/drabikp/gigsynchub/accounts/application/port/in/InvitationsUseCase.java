package sk.drabikp.gigsynchub.accounts.application.port.in;

import sk.drabikp.gigsynchub.accounts.application.port.in.BandsUseCase.MyBand;
import sk.drabikp.gigsynchub.accounts.domain.Invitation;
import sk.drabikp.gigsynchub.accounts.domain.Role;
import sk.drabikp.gigsynchub.accounts.domain.UserId;
import sk.drabikp.gigsynchub.gig.domain.BandId;

import java.util.List;
import java.util.UUID;

/**
 * Bringing people into a band: an owner invites an e-mail address as a role and gets a link (its token
 * shown once — the owner passes it on); whoever is signed in with that address and opens the link becomes
 * the band's member. A link is valid for {@link #VALID_DAYS} days, once.
 */
public interface InvitationsUseCase {

    int VALID_DAYS = 7;

    /** A new invitation and its link's token (never kept — only its hash). */
    record Created(Invitation invitation, String token) {
    }

    /** What the link offers the signed-in user: the band, the role, whom it's for, and whether they can accept. */
    record Offer(String bandName, Role role, String maskedEmail, boolean open, boolean forYou, boolean member) {
    }

    /**
     * Invites {@code email} into the band as {@code role} (owners only); an open invitation for the same address
     * is replaced. Refused for an address that isn't one or whose user is a member already.
     */
    Created invite(UserId owner, BandId band, String email, Role role);

    /** The band's open invitations (members). */
    List<Invitation> open(UserId member, BandId band);

    /** Revokes an open invitation (owners only). */
    void revoke(UserId owner, BandId band, UUID invitation);

    /** What the link with {@code token} offers the user; NotFoundException for a token that names nothing. */
    Offer offer(UserId user, String token);

    /** Accepts the invitation: the user becomes the band's member. */
    MyBand accept(UserId user, String token);
}
