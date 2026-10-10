package sk.drabikp.bzscraper.bandzone;

import sk.drabikp.bzscraper.accounts.application.port.in.PlatformAccounts;
import sk.drabikp.bzscraper.accounts.domain.PlatformAccount;
import sk.drabikp.bzscraper.gig.domain.BandId;

import java.util.Locale;

/**
 * A band's Bandzone account, as the band connected it ({@link PlatformAccounts}): its login, password and
 * the band's address on Bandzone ({@code bandzone.cz/<bandSlug>}). {@link #toString} hides the password.
 */
public record BandzoneAccount(BandId band, String login, String password, String bandSlug) {

    /** The account's field names (see {@link BandzonePlatform}). */
    public static final String LOGIN = "login";
    public static final String PASSWORD = "password";
    public static final String BAND_SLUG = "bandSlug";

    /** The band's Bandzone account; needing the user when the band hasn't connected Bandzone. */
    public static BandzoneAccount of(PlatformAccounts accounts, BandId band) throws BandzonePortalException {
        PlatformAccount account = accounts.of(band, BandzonePlatform.PLATFORM).orElseThrow(() ->
                BandzonePortalException.needsUser("Bandzone isn't connected for this band — connect it in Settings."));
        return new BandzoneAccount(band, account.setting(LOGIN), account.secret(PASSWORD),
                account.setting(BAND_SLUG).toLowerCase(Locale.ROOT));
    }

    /** Whose saved login a browser profile holds: this band's, with this login. */
    public String browserKey() {
        return band + ":" + login;
    }

    @Override
    public String toString() {
        return "BandzoneAccount[band=" + band + ", login=" + login + ", bandSlug=" + bandSlug + "]";
    }
}
