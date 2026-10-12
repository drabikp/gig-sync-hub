package sk.drabikp.gigsynchub.check.domain;

import org.jmolecules.ddd.annotation.ValueObject;
import sk.drabikp.gigsynchub.gig.domain.GigId;
import sk.drabikp.gigsynchub.gig.domain.platform.Platform;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The result of reading the platforms and comparing them with the catalog.
 *
 * @param drifts     the published gigs whose platform copy no longer matches
 * @param unreadable platforms that couldn't be read this time, with why
 * @param unlinked   per platform, how many of its events no catalog gig is published as (see Import)
 */
@ValueObject
public record PlatformCheck(Instant checkedAt, List<Drift> drifts, Map<Platform, String> unreadable,
                            Map<Platform, Integer> unlinked) {

    public PlatformCheck {
        drifts = List.copyOf(drifts);
        unreadable = Map.copyOf(unreadable);
        unlinked = Map.copyOf(unlinked);
    }

    /** This check without the gig's difference on the platform (fixed, or not worth fixing). */
    public PlatformCheck without(Platform platform, GigId gigId) {
        return new PlatformCheck(checkedAt, drifts.stream()
                .filter(d -> !d.platform().equals(platform) || !d.gigId().equals(gigId)).toList(),
                unreadable, unlinked);
    }
}
