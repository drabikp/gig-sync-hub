package sk.drabikp.bzscraper.accounts.application.port.in;

import sk.drabikp.bzscraper.accounts.domain.PlatformAccount;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;

import java.util.Map;
import java.util.Optional;

/**
 * The bands' platform accounts, for the platforms: a band's account is read the moment a platform
 * signs in for it (its secrets opened only then).
 */
public interface PlatformAccounts {

    /** The band's account on the platform; empty when the band hasn't connected it. */
    Optional<PlatformAccount> of(BandId band, Platform platform);

    /**
     * Saves an account the server's configuration still had (band #1's, from before each band had its
     * own) — only when the band has none on that platform yet and this server can keep secrets.
     *
     * @return true when it was saved
     */
    boolean adopt(BandId band, Platform platform, Map<String, String> settings, Map<String, String> secrets);
}
