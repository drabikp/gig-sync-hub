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
    SECRETS_UNAVAILABLE("secretsUnavailable");

    private final String code;

    AccountsErrorCode(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
