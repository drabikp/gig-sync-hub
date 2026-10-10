package sk.drabikp.bzscraper.check.application.port.out;

import sk.drabikp.bzscraper.check.domain.PlatformCheck;
import sk.drabikp.bzscraper.gig.domain.BandId;

import java.util.Optional;

/** Each band's latest platform check, kept across restarts. */
public interface CheckResultStore {

    Optional<PlatformCheck> of(BandId band);

    /** Replaces the band's latest check. */
    void save(BandId band, PlatformCheck check);
}
