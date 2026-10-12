package sk.drabikp.bzscraper.sync.application.engine;

import org.jmolecules.architecture.hexagonal.Application;
import sk.drabikp.bzscraper.gig.application.port.out.GigRepository;
import sk.drabikp.bzscraper.gig.application.port.out.PublishedGigStore;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.gig.domain.platform.Platforms;
import sk.drabikp.bzscraper.sync.application.port.out.SyncStep;
import sk.drabikp.bzscraper.sync.domain.MootWork;
import sk.drabikp.bzscraper.sync.domain.SyncAction;
import sk.drabikp.bzscraper.sync.domain.SyncTask;

import java.util.Optional;

/**
 * What a step is to do for one run, asked by the {@link WorkflowEngine} just before it uses the
 * step: the gig as the catalog has it NOW (a task works from the state at the time it runs, so
 * work that has become pointless ends here — {@link MootWork}), the platform's own id for its
 * copy, and whether this step takes this gig at all.
 *
 * <p>It only answers ({@link Intake}). Writing down what follows is the engine's, because what
 * follows a step that won't take a gig is the workflow's next step, and the engine is what knows
 * the workflow.
 */
@Application
public class RunIntake {

    private final GigRepository gigRepository;
    private final PublishedGigStore publishedGigStore;
    private final Platforms platforms;

    public RunIntake(GigRepository gigRepository, PublishedGigStore publishedGigStore, Platforms platforms) {
        this.gigRepository = gigRepository;
        this.publishedGigStore = publishedGigStore;
        this.platforms = platforms;
    }

    Intake at(SyncStep step, SyncTask run) {
        Platform platform = run.platform();
        Gig gig = gigRepository.findById(run.band(), run.gigId()).orElse(null);
        boolean published = publishedGigStore.isPublished(run.band(), platform, run.gigId());
        Optional<String> moot = MootWork.reason(run.action(), gig, published, platforms.name(platform));
        if (moot.isPresent()) {
            return new Intake.Moot(moot.get());
        }
        String ref = publishedGigStore.externalRef(run.band(), platform, run.gigId()).orElse(null);
        if (run.action() != SyncAction.PUBLISH && ref == null) {
            return new Intake.ByHand("no " + platforms.name(platform) + " id was recorded — do it there by hand");
        }
        // a gig that has left the catalog is taken as it stands: what could be done with it was
        // asked when the work was queued, and there is nothing left to ask a refusal about
        Optional<String> refusal = gig == null ? Optional.empty() : step.refusal(gig);
        return refusal.isPresent() ? new Intake.PassedOn(refusal.get())
                : new Intake.Take(new SyncStep.Item(run.band(), ref, gig));
    }
}
