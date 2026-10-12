package sk.drabikp.gigsynchub.bandsintown;

import sk.drabikp.gigsynchub.accounts.application.port.in.PlatformAccounts;
import sk.drabikp.gigsynchub.accounts.domain.PlatformAccount;
import sk.drabikp.gigsynchub.gig.domain.BandId;

/**
 * A band's Bandsintown account, as the band connected it ({@link PlatformAccounts}): the artist as
 * Bandsintown spells it (every uploaded event carries it), whether its followers hear of new events, and
 * the way in — the refresh token the band's own browser sign-in left, with the two numbers the portal's
 * addresses are built from and the fingerprint we made up for this band.
 *
 * <p>No password and no authenticator secret: the portal's sign-in only ever happens in the band's browser,
 * so the server holds nothing but a session the band can revoke by signing out there.
 * {@link #toString} hides the token.
 */
public record BandsintownAccount(BandId band, String refreshToken, String managerId, String actorId,
                                 String fingerprint, String artistName, boolean notifyFollowers) {

    /** The account's field names (see {@link BandsintownPlatform}). */
    public static final String ARTIST_NAME = "artistName";
    public static final String NOTIFY_FOLLOWERS = "notifyFollowers";
    /** What the band pastes from the portal's page; signing in turns it into the three below. */
    public static final String CONNECTION = "connection";
    public static final String REFRESH_TOKEN = "refreshToken";
    public static final String MANAGER_ID = "managerId";
    public static final String ACTOR_ID = "actorId";
    public static final String FINGERPRINT = "fingerprint";

    /** The band's Bandsintown account; needing the user when the band hasn't connected Bandsintown. */
    public static BandsintownAccount of(PlatformAccounts accounts, BandId band) throws BitPortalException {
        PlatformAccount account = accounts.of(band, BandsintownPlatform.PLATFORM).orElseThrow(() ->
                BitPortalException.needsUser("Bandsintown isn't connected for this band — connect it in Settings."));
        BandsintownAccount connected = new BandsintownAccount(band, account.secret(REFRESH_TOKEN),
                account.setting(MANAGER_ID), account.setting(ACTOR_ID), account.setting(FINGERPRINT),
                account.setting(ARTIST_NAME), account.flag(NOTIFY_FOLLOWERS));
        if (connected.refreshToken().isBlank() || connected.managerId().isBlank() || connected.actorId().isBlank()) {
            throw BitPortalException.needsUser("This band's Bandsintown sign-in is incomplete — sign in at "
                    + "artists.bandsintown.com and connect it in Settings again.");
        }
        return connected;
    }

    @Override
    public String toString() {
        return "BandsintownAccount[band=" + band + ", artistName=" + artistName + ", managerId=" + managerId
                + ", actorId=" + actorId + ", notifyFollowers=" + notifyFollowers + ", refreshToken="
                + (refreshToken.isBlank() ? "" : "***") + "]";
    }
}
