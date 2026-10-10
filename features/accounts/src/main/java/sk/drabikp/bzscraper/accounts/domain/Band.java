package sk.drabikp.bzscraper.accounts.domain;

import sk.drabikp.bzscraper.gig.domain.BandId;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A band the app manages: its id (never changes), its name, and its {@code slug} — its name in
 * addresses ({@code /b/<slug>}, the public {@code /gigs/<slug>}): lower case letters, digits and
 * dashes, 2–64 of them.
 */
public record Band(BandId id, String slug, String name) {

    private static final Pattern SLUG = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,62}[a-z0-9])");

    public Band {
        Objects.requireNonNull(id, "a band's id");
        if (!validSlug(slug)) {
            throw new IllegalArgumentException("not a band's address: " + slug);
        }
        if (!validName(name)) {
            throw new IllegalArgumentException("a band's name is 1 to 100 characters");
        }
        name = name.strip();
    }

    public static boolean validSlug(String slug) {
        return slug != null && SLUG.matcher(slug).matches();
    }

    public static boolean validName(String name) {
        return name != null && !name.isBlank() && name.strip().length() <= 100;
    }

    public Band renamed(String newName, String newSlug) {
        return new Band(id, newSlug, newName);
    }
}
