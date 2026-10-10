package sk.drabikp.bzscraper.bandzone.portal;

import sk.drabikp.bzscraper.gig.domain.Country;
import sk.drabikp.bzscraper.gig.domain.TextFold;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Picks the gig's town among Bandzone's city suggestions. Bandzone has its own list of Czech
 * and Slovak towns, each shown as a name and a description "okres Přerov, kraj Olomoucký"
 * (Slovak ones end with "Slovensko"); names repeat a lot (three Hranice, two Czech Košice
 * besides the Slovak city). A suggestion fits with the same name (accents and case ignored),
 * the same country and — when the gig's town was picked from the place search — the same
 * district. Exactly one fit or an explanation: never the first one that starts alike.
 */
public final class BandzoneTowns {

    /** One suggestion: its position in the list, the name and the description. */
    public record Suggestion(int index, String name, String description) {

        boolean slovak() {
            return TextFold.fold(description).contains("slovensko");
        }
    }

    /** The suggestion to pick, or why there is none. */
    public record Choice(Suggestion pick, String problem) {
    }

    private BandzoneTowns() {
    }

    public static Choice choose(String city, Country country, String district, List<Suggestion> suggestions) {
        List<Suggestion> fits = suggestions.stream()
                .filter(s -> TextFold.fold(s.name()).equals(TextFold.fold(city)))
                .filter(s -> country == null || s.slovak() == (country == Country.SLOVAKIA))
                .toList();
        if (district != null) {
            fits = fits.stream()
                    .filter(s -> TextFold.fold(s.description()).contains(TextFold.fold(district)))
                    .toList();
        }
        if (fits.size() == 1) {
            return new Choice(fits.getFirst(), null);
        }
        String where = city + (district != null ? " (" + district + ")" : "");
        if (fits.isEmpty()) {
            return new Choice(null, "Bandzone doesn't know the town " + where
                    + " — correct the gig's city (the town's name, not a district) and try again.");
        }
        return new Choice(null, "Bandzone knows several towns named " + city + ": "
                + fits.stream().map(Suggestion::description).collect(Collectors.joining("; "))
                + " — pick the town from the list in the gig form, so it's clear which one.");
    }
}
