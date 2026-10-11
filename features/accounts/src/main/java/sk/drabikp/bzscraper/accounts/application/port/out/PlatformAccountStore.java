package sk.drabikp.bzscraper.accounts.application.port.out;

import org.jmolecules.ddd.annotation.Repository;
import sk.drabikp.bzscraper.accounts.domain.PlatformAccount;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;

import java.util.List;
import java.util.Optional;

/** The bands' platform accounts, their secrets kept encrypted. */
@Repository
public interface PlatformAccountStore {

    Optional<PlatformAccount> of(BandId band, Platform platform);

    List<PlatformAccount> ofBand(BandId band);

    void save(PlatformAccount account);

    void delete(BandId band, Platform platform);

    /** Seals again every account's secrets kept with an older key; how many. */
    int reseal();
}
