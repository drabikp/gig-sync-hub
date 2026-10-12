package sk.drabikp.gigsynchub.accounts.application.port.in;

import sk.drabikp.gigsynchub.accounts.domain.AccountCheck;
import sk.drabikp.gigsynchub.accounts.domain.UserId;
import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.gig.domain.platform.Platform;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A band's platform accounts on the page: what is connected (never a secret — only which are set),
 * connecting and changing one, disconnecting it (owners), and testing that it signs in (members).
 */
public interface PlatformAccountsUseCase {

    /** An account as the page sees it: its settings, the names of the secrets set, its last test. */
    record AccountView(Platform platform, boolean connected, Map<String, String> settings, Set<String> secretsSet,
                       AccountCheck lastCheck) {
    }

    /** Every platform that has an account, connected or not, in the platforms' order. */
    List<AccountView> accounts(UserId user, BandId band);

    /**
     * Saves the band's account on the platform (owners only): the settings as given, each secret given
     * replaces the one kept (a blank one keeps it). Refused when a required field is missing or a field
     * isn't the platform's ({@link AccountsErrorCode#INVALID_ACCOUNT}). The last test is forgotten.
     */
    AccountView connect(UserId user, BandId band, Platform platform, Map<String, String> settings,
                        Map<String, String> secrets);

    /** Forgets the band's account on the platform, secrets and all (owners only). */
    void disconnect(UserId user, BandId band, Platform platform);

    /** Signs in to the platform with the band's account once and keeps what it said. */
    AccountView check(UserId user, BandId band, Platform platform);
}
