package sk.drabikp.gigsynchub.sync.application.engine;

import org.jmolecules.architecture.hexagonal.Application;
import sk.drabikp.gigsynchub.gig.domain.Gig;
import sk.drabikp.gigsynchub.gig.domain.platform.Platform;
import sk.drabikp.gigsynchub.sync.domain.SyncAction;

import java.util.Optional;

/** Whether some step can do the work, asked when it is queued: why not, or empty when it can. */
@Application
public interface SyncAdmission {

    /** Everything is admitted (tests that don't care about workflows). */
    SyncAdmission ALL = (platform, action, gig) -> Optional.empty();

    Optional<String> leftOut(Platform platform, SyncAction action, Gig gig);
}
