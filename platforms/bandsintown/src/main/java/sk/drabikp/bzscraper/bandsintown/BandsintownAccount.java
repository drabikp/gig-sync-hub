package sk.drabikp.bzscraper.bandsintown;

import sk.drabikp.bzscraper.accounts.application.port.in.PlatformAccounts;
import sk.drabikp.bzscraper.accounts.domain.PlatformAccount;
import sk.drabikp.bzscraper.gig.domain.BandId;

/**
 * A band's Bandsintown account, as the band connected it ({@link PlatformAccounts}): the artist portal's
 * login, password and authenticator secret (base32), the artist as Bandsintown spells it (every uploaded row
 * carries it), the artist's id (when the account manages several) and whether followers hear of new events.
 * {@link #toString} hides the secrets.
 */
public record BandsintownAccount(BandId band, String login, String password, String totpSecret, String artistId,
                                 String artistName, boolean notifyFollowers) {

    /** The account's field names (see {@link BandsintownPlatform}). */
    public static final String LOGIN = "login";
    public static final String PASSWORD = "password";
    public static final String TOTP_SECRET = "totpSecret";
    public static final String ARTIST_NAME = "artistName";
    public static final String ARTIST_ID = "artistId";
    public static final String NOTIFY_FOLLOWERS = "notifyFollowers";

    /** The band's Bandsintown account; needing the user when the band hasn't connected Bandsintown. */
    public static BandsintownAccount of(PlatformAccounts accounts, BandId band) throws BitPortalException {
        PlatformAccount account = accounts.of(band, BandsintownPlatform.PLATFORM).orElseThrow(() ->
                BitPortalException.needsUser("Bandsintown isn't connected for this band — connect it in Settings."));
        return new BandsintownAccount(band, account.setting(LOGIN), account.secret(PASSWORD),
                account.secret(TOTP_SECRET), account.setting(ARTIST_ID), account.setting(ARTIST_NAME),
                account.flag(NOTIFY_FOLLOWERS));
    }

    /** Whose saved login a browser profile holds: this band's, with this login. */
    public String browserKey() {
        return band + ":" + login;
    }

    @Override
    public String toString() {
        return "BandsintownAccount[band=" + band + ", login=" + login + ", artistId=" + artistId + ", artistName="
                + artistName + ", notifyFollowers=" + notifyFollowers + ", totpSecret="
                + (totpSecret.isBlank() ? "" : "***") + "]";
    }
}
