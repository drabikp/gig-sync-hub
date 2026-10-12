package sk.drabikp.gigsynchub.gig.domain;

import org.jmolecules.ddd.annotation.Service;

import java.util.Map;

/**
 * Compares city names as platforms write them — some in English ("Prague", "Pilsen"), some
 * with a district number ("Vsetín 1"), some in the local language ("Praha", "Plzeň").
 * Accents, case, spacing and a trailing district number are ignored, and the English names
 * of Czech and Slovak cities count as the local ones.
 */
@Service
public final class CityName {

    private static final Map<String, String> ENGLISH_NAMES = Map.of(
            "prague", "praha",
            "pilsen", "plzen",
            "budweis", "ceske budejovice",
            "olmutz", "olomouc",
            "brunn", "brno");

    private CityName() {
    }

    /** A comparison key: equal keys = the same city. */
    public static String key(String city) {
        String plain = TextFold.fold(city).replaceAll(" \\d+$", "");
        return ENGLISH_NAMES.getOrDefault(plain, plain);
    }

    public static boolean same(String a, String b) {
        return key(a).equals(key(b));
    }
}
