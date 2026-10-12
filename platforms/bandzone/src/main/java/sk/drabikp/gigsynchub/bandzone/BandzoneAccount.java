package sk.drabikp.gigsynchub.bandzone;

import sk.drabikp.gigsynchub.accounts.application.port.in.PlatformAccounts;
import sk.drabikp.gigsynchub.accounts.domain.PlatformAccount;
import sk.drabikp.gigsynchub.gig.domain.BandId;

import java.util.Locale;

/**
 * A band's Bandzone account, as the band connected it ({@link PlatformAccounts}): its login, the band's
 * address on Bandzone ({@code bandzone.cz/<bandSlug>}), and the way in — the {@code session} Bandzone gave
 * when the band connected (what is normally kept; it lasts years) or, where this server drives Bandzone in
 * a browser, the {@code password}. {@link #toString} hides both.
 */
public record BandzoneAccount(BandId band, String login, String password, String session, String bandSlug) {

    /** The account's field names (see {@link BandzonePlatform}). */
    public static final String LOGIN = "login";
    public static final String PASSWORD = "password";
    /** Not a field a band fills in: what signing in once gave us, kept in the password's place. */
    public static final String SESSION = "session";
    public static final String BAND_SLUG = "bandSlug";

    /** The band's Bandzone account; needing the user when the band hasn't connected Bandzone. */
    public static BandzoneAccount of(PlatformAccounts accounts, BandId band) throws BandzonePortalException {
        PlatformAccount account = accounts.of(band, BandzonePlatform.PLATFORM).orElseThrow(() ->
                BandzonePortalException.needsUser("Bandzone isn't connected for this band — connect it in Settings."));
        return new BandzoneAccount(band, account.setting(LOGIN), account.secret(PASSWORD),
                account.secret(SESSION), account.setting(BAND_SLUG).toLowerCase(Locale.ROOT));
    }

    /** Whose saved login a browser profile holds: this band's, with this login. */
    public String browserKey() {
        return band + ":" + login;
    }

    @Override
    public String toString() {
        return "BandzoneAccount[band=" + band + ", login=" + login + ", bandSlug=" + bandSlug + ", session="
                + (session.isBlank() ? "" : "***") + ", password=" + (password.isBlank() ? "" : "***") + "]";
    }
}
