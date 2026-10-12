package sk.drabikp.gigsynchub.sync.application.engine;

import org.jmolecules.architecture.hexagonal.Application;
import sk.drabikp.gigsynchub.gig.domain.Gig;
import sk.drabikp.gigsynchub.gig.domain.platform.Platform;
import sk.drabikp.gigsynchub.gig.domain.platform.Platforms;
import sk.drabikp.gigsynchub.sync.application.port.out.SyncStep;
import sk.drabikp.gigsynchub.sync.domain.StepType;
import sk.drabikp.gigsynchub.sync.domain.SyncAction;
import sk.drabikp.gigsynchub.sync.domain.Workflows;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The sync steps each platform provides ({@link SyncStep}, at most one per type), and the two
 * questions about them: which step a platform has for a type, and — before any work is queued —
 * whether any step of the workflow could take this gig at all ({@link SyncAdmission}, so that the
 * user hears "left out" at once). Where a run already queued goes next is NOT asked here: the
 * {@link WorkflowEngine} walks the workflow itself. A platform without a step of its own for
 * "remove and create again" gets one built from its remove and create steps ({@link Recreator},
 * {@link RecreateStep}).
 */
@Application
public class StepRegistry implements SyncAdmission {

    /** Builds a platform's "remove and create again" from its remove steps (in order) and its create step. */
    @FunctionalInterface
    public interface Recreator {
        SyncStep recreate(List<SyncStep> removes, SyncStep create);
    }

    private final Map<Platform, Map<StepType, SyncStep>> steps = new HashMap<>();
    private final Platforms platforms;
    private final Recreator recreator;

    public StepRegistry(List<SyncStep> steps, Platforms platforms, Recreator recreator) {
        for (SyncStep step : steps) {
            if (this.steps.computeIfAbsent(step.platform(), p -> new EnumMap<>(StepType.class))
                    .put(step.type(), step) != null) {
                throw new IllegalStateException("Two " + step.type() + " steps registered for " + step.platform());
            }
        }
        this.platforms = platforms;
        this.recreator = recreator;
    }

    @Override
    public Optional<String> leftOut(Platform platform, SyncAction action, Gig gig) {
        List<String> reasons = new ArrayList<>();
        return firstTaking(platform, Workflows.of(action).getFirst(), gig, reasons).isPresent()
                ? Optional.empty() : Optional.of(byHand(platform, reasons));
    }

    /**
     * From {@code from} on, the first step the platform has that takes the gig; why the others
     * didn't goes to {@code reasons}. This is the question {@link #leftOut} asks before any work
     * is queued — "can anything here do this at all" — walked ahead of time. What happens to work
     * already queued is not asked this way: the {@link WorkflowEngine} walks the workflow itself,
     * one step at a time, and writes down each hop as it makes it.
     */
    private Optional<StepType> firstTaking(Platform platform, StepType from, Gig gig, List<String> reasons) {
        for (StepType type = from; type != null; type = Workflows.after(type).orElse(null)) {
            SyncStep step = step(platform, type);
            if (step == null) {
                reasons.add(type.label() + ": none for " + platforms.name(platform));
                continue;
            }
            Optional<String> refusal = gig == null ? Optional.empty() : step.refusal(gig);
            if (refusal.isEmpty()) {
                return Optional.of(type);
            }
            reasons.add(type.label() + ": " + refusal.get());
        }
        return Optional.empty();
    }

    String byHand(Platform platform, List<String> reasons) {
        return "nothing here can do this on " + platforms.name(platform) + " — do it there by hand ("
                + String.join("; ", reasons) + ")";
    }

    /** The platform's step of that type — for RECREATE, else built from its remove and create steps. */
    SyncStep step(Platform platform, StepType type) {
        Map<StepType, SyncStep> own = steps.getOrDefault(platform, Map.of());
        if (own.containsKey(type) || type != StepType.RECREATE) {
            return own.get(type);
        }
        List<SyncStep> removes = Stream.of(StepType.REMOVE, StepType.FORM_REMOVE)
                .map(own::get).filter(Objects::nonNull).toList();
        SyncStep create = own.containsKey(StepType.FORM_CREATE) ? own.get(StepType.FORM_CREATE)
                : own.get(StepType.BULK_CREATE);
        return removes.isEmpty() || create == null ? null : recreator.recreate(removes, create);
    }
}
