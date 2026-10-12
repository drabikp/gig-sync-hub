package sk.drabikp.gigsynchub.check.application.port.out;

import org.jmolecules.ddd.annotation.Repository;
import sk.drabikp.gigsynchub.check.domain.PlatformCheck;
import sk.drabikp.gigsynchub.gig.domain.BandId;

import java.util.Optional;

/** Each band's latest platform check, kept across restarts. */
@Repository
public interface CheckResultStore {

    Optional<PlatformCheck> of(BandId band);

    /** Replaces the band's latest check. */
    void save(BandId band, PlatformCheck check);
}
