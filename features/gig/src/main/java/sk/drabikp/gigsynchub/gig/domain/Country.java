package sk.drabikp.gigsynchub.gig.domain;

import org.jmolecules.ddd.annotation.ValueObject;

import java.util.Map;
import java.util.Optional;

/**
 * A country a gig can take place in. Carries its English display
 * name and an IANA timezone, both required by the BIT CSV import. Scoped to the
 * band's actual markets; extend as needed.
 */
@ValueObject
public enum Country {
    CZECHIA("Czechia", "Europe/Prague"),
    SLOVAKIA("Slovakia", "Europe/Bratislava");

    private final String displayName;
    private final String timezone;

    Country(String displayName, String timezone) {
        this.displayName = displayName;
        this.timezone = timezone;
    }

    /** The country's short English name (e.g. "Czechia", not "Czech Republic"). */
    public String displayName() {
        return displayName;
    }

    /** IANA timezone id (e.g. "Europe/Prague"). */
    public String timezone() {
        return timezone;
    }

    /** The country a written name means — English or local ("Česko", "Slovensko"), any case or accents. */
    public static Optional<Country> named(String name) {
        return Optional.ofNullable(NAMES.get(TextFold.fold(name)));
    }

    private static final Map<String, Country> NAMES = Map.ofEntries(
            Map.entry("czechia", CZECHIA), Map.entry("czech republic", CZECHIA), Map.entry("cesko", CZECHIA),
            Map.entry("ceska republika", CZECHIA), Map.entry("cr", CZECHIA), Map.entry("cz", CZECHIA),
            Map.entry("slovakia", SLOVAKIA), Map.entry("slovensko", SLOVAKIA),
            Map.entry("slovenska republika", SLOVAKIA), Map.entry("sr", SLOVAKIA), Map.entry("sk", SLOVAKIA));
}
