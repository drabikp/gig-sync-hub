package sk.drabikp.gigsynchub.gig.domain.platform;

import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;
import sk.drabikp.gigsynchub.gig.domain.GigId;

/**
 * A catalog gig's copy on one platform: the platform's id for it ({@code externalRef}),
 * or null when the platform gave none back.
 */
@AggregateRoot
public record Publication(@Identity Platform platform, @Identity GigId gigId, String externalRef) {

    public boolean hasExternalRef() {
        return externalRef != null && !externalRef.isBlank();
    }
}
