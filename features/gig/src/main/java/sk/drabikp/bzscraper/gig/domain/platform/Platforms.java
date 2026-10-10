package sk.drabikp.bzscraper.gig.domain.platform;

import sk.drabikp.bzscraper.gig.domain.UnknownReferenceException;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The platforms the app knows — each adapter contributes its {@link PlatformTraits} — in
 * import-precedence order. A platform the database mentions but no adapter provides anymore
 * still has a name (its id) and plain traits.
 */
public final class Platforms {

    private final Map<Platform, PlatformTraits> traits = new LinkedHashMap<>();

    public Platforms(List<PlatformTraits> all) {
        all.stream().sorted(Comparator.comparingInt(PlatformTraits::importPrecedence)
                        .thenComparing(PlatformTraits::platform))
                .forEach(t -> {
                    if (traits.put(t.platform(), t) != null) {
                        throw new IllegalStateException("Two adapters describe platform " + t.platform());
                    }
                });
    }

    public List<Platform> all() {
        return List.copyOf(traits.keySet());
    }

    public List<PlatformTraits> traits() {
        return List.copyOf(traits.values());
    }

    /** These platforms in the registry's order (import precedence), unknown ones after them by id. */
    public List<Platform> ordered(Collection<Platform> some) {
        return some.stream().distinct().sorted(Comparator.comparingInt((Platform p) -> traits(p).importPrecedence())
                .thenComparing(Comparator.naturalOrder())).toList();
    }

    public Optional<Platform> find(String id) {
        return traits.keySet().stream().filter(p -> p.id().equals(id)).findFirst();
    }

    /** The platform with this id; {@link UnknownReferenceException} when no adapter provides it. */
    public Platform get(String id) {
        return find(id).orElseThrow(() -> new UnknownReferenceException("not a platform: " + id));
    }

    /** The platforms with these ids, in the order given (none for null). */
    public List<Platform> get(Collection<String> ids) {
        return ids == null ? List.of() : ids.stream().distinct().map(this::get).toList();
    }

    public PlatformTraits traits(Platform platform) {
        PlatformTraits known = traits.get(platform);
        return known != null ? known
                : PlatformTraits.of(platform, platform.id()).build();
    }

    public String name(Platform platform) {
        return traits(platform).displayName();
    }
}
