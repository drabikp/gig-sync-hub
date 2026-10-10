package sk.drabikp.bzscraper.accounts.application.port.in;

import sk.drabikp.bzscraper.gig.application.ErrorCode;

/** What the accounts say no with. */
public enum AccountsErrorCode implements ErrorCode {

    /** Another band has that address; {@code slug}. */
    SLUG_TAKEN("slugTaken"),
    /** A band's name or address that isn't one; {@code fields}: name, slug. */
    INVALID_BAND("invalidBand"),
    /** Only the band's owners may do that. */
    OWNER_ONLY("ownerOnly"),
    /** A platform account missing what it needs, or with what the platform doesn't have; {@code fields}. */
    INVALID_ACCOUNT("invalidAccount"),
    /** This server has no key to keep secrets with ({@code bzscraper.accounts.data-keys}). */
    SECRETS_UNAVAILABLE("secretsUnavailable"),
    /** Not an e-mail address; {@code email}. */
    INVALID_EMAIL("invalidEmail"),
    /** Whoever has that address is the band's member already; {@code email}. */
    ALREADY_MEMBER("alreadyMember"),
    /** The invitation was accepted, revoked or has expired. */
    INVITATION_GONE("invitationGone"),
    /** The invitation is for another address than the one signed in; {@code email} (masked). */
    INVITATION_NOT_FOR_YOU("invitationNotForYou"),
    /** A band keeps at least one owner: hand it over first. */
    LAST_OWNER("lastOwner"),
    /** Deleting a band needs its address typed in. */
    CONFIRM_DELETE("confirmDelete"),
    /** A user may own only so many bands; {@code max}. */
    TOO_MANY_BANDS("tooManyBands");

    private final String code;

    AccountsErrorCode(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
