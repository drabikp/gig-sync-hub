package sk.drabikp.bzscraper.sync.application.port.out;

import org.jmolecules.ddd.annotation.Repository;
import sk.drabikp.bzscraper.gig.domain.BandId;

import java.util.Map;
import java.util.Optional;

/** A band's small settings that survive restarts (e.g. its sync paused by the user). */
@Repository
public interface SettingsStore {

    Optional<String> get(BandId band, String name);

    /** Sets the band's {@code name}; {@code null} removes it. */
    void put(BandId band, String name, String value);

    /** Every band's value of {@code name} (the bands without it left out). */
    Map<BandId, String> all(String name);
}
