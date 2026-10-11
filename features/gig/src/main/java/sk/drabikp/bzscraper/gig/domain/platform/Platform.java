package sk.drabikp.bzscraper.gig.domain.platform;

import org.jmolecules.ddd.annotation.ValueObject;

import java.util.regex.Pattern;

/**
 * A listing platform the gigs are published to, by its id (upper case, e.g. "MYPLATFORM") — the core knows no
 * platform by name: what one is and can do comes from its adapter ({@link PlatformTraits}
 * in {@link Platforms}, and the sync steps it provides). The id is what the database keeps.
 */
@ValueObject
public record Platform(String id) implements Comparable<Platform> {

    private static final Pattern ID = Pattern.compile("[A-Z][A-Z0-9_]*");

    public Platform {
        if (id == null || !ID.matcher(id).matches()) {
            throw new IllegalArgumentException("a platform id is upper case letters, digits and _: " + id);
        }
    }

    public static Platform of(String id) {
        return new Platform(id);
    }

    @Override
    public int compareTo(Platform other) {
        return id.compareTo(other.id);
    }

    @Override
    public String toString() {
        return id;
    }
}
