package sk.drabikp.bzscraper.accounts.application;

import sk.drabikp.bzscraper.accounts.application.port.in.PlatformAccounts;
import sk.drabikp.bzscraper.accounts.application.port.in.SecretBox;
import sk.drabikp.bzscraper.accounts.application.port.out.PlatformAccountStore;
import sk.drabikp.bzscraper.accounts.domain.PlatformAccount;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.gig.domain.platform.Platforms;

import java.util.Map;
import java.util.Optional;

/**
 * The bands' platform accounts as the platforms read them; and the accounts the configuration still had
 * (band #1's, from before each band connected its own) saved once ({@link #adopt}). Keys rotate here too:
 * {@link #reseal} seals again what an older key sealed.
 */
public class PlatformAccountDirectory implements PlatformAccounts {

    private final PlatformAccountStore store;
    private final Platforms platforms;
    private final SecretBox box;

    public PlatformAccountDirectory(PlatformAccountStore store, Platforms platforms, SecretBox box) {
        this.store = store;
        this.platforms = platforms;
        this.box = box;
    }

    @Override
    public Optional<PlatformAccount> of(BandId band, Platform platform) {
        return store.of(band, platform);
    }

    @Override
    public boolean adopt(BandId band, Platform platform, Map<String, String> settings, Map<String, String> secrets) {
        if (!box.ready() || store.of(band, platform).isPresent()) {
            return false;
        }
        store.save(AccountForm.valid(band, platforms.traits(platform), settings, secrets, null, box));
        return true;
    }

    /** Seals again every account an older key sealed; how many. */
    public int reseal() {
        return box.ready() ? store.reseal() : 0;
    }
}
