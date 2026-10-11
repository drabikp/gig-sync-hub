package sk.drabikp.bzscraper.sync.domain;

import org.jmolecules.ddd.annotation.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The sync workflows: for each action, the ways of doing it in the order they are tried —
 * cheapest and sturdiest first (one bulk upload for many gigs before a form per gig). What
 * no step can do is left to the user.
 */
@Service
public final class Workflows {

    private static final Map<SyncAction, List<StepType>> STEPS = new EnumMap<>(Map.of(
            SyncAction.PUBLISH, List.of(StepType.BULK_CREATE, StepType.FORM_CREATE),
            SyncAction.UPDATE, List.of(StepType.BULK_EDIT, StepType.FORM_EDIT),
            SyncAction.CANCEL, List.of(StepType.CANCEL, StepType.FORM_CANCEL),
            SyncAction.DELETE, List.of(StepType.REMOVE, StepType.FORM_REMOVE),
            SyncAction.REACTIVATE, List.of(StepType.RECREATE)));

    static {
        for (SyncAction action : SyncAction.values()) {
            if (!STEPS.containsKey(action)) {
                throw new IllegalStateException("no workflow for " + action);
            }
        }
    }

    private Workflows() {
    }

    /** The action's steps, in the order they are tried (every action has a workflow). */
    public static List<StepType> of(SyncAction action) {
        return STEPS.get(action);
    }

    /** The step after {@code step} in its workflow, if any. */
    public static Optional<StepType> after(StepType step) {
        List<StepType> steps = STEPS.get(step.action());
        int next = steps.indexOf(step) + 1;
        return next < steps.size() ? Optional.of(steps.get(next)) : Optional.empty();
    }
}
