package sk.drabikp.bzscraper.sync.domain;

import org.jmolecules.ddd.annotation.ValueObject;

/**
 * A kind of work a sync workflow is made of — one way of doing (part of) an action on a
 * platform. Platforms contribute implementations of the step types they support; the
 * engine runs a run's steps in the workflow's order and skips the ones its platform lacks.
 */
@ValueObject
public enum StepType {

    /** Create many events in one go (e.g. one file upload, then publish). */
    BULK_CREATE(SyncAction.PUBLISH, "bulk upload"),
    /** Create one event at a time through the platform's form (e.g. a wizard, then the edit form). */
    FORM_CREATE(SyncAction.PUBLISH, "create form"),
    /** Edit many events in one go (e.g. one file upload carrying the event ids). */
    BULK_EDIT(SyncAction.UPDATE, "bulk edit"),
    /** Edit one event at a time through the platform's edit form. */
    FORM_EDIT(SyncAction.UPDATE, "form edit"),
    /** Mark the event cancelled (a platform without a cancelled state removes it instead). */
    CANCEL(SyncAction.CANCEL, "cancel"),
    /** Cancel one event through its own form (e.g. a past event, which the platform's list can't reach). */
    FORM_CANCEL(SyncAction.CANCEL, "cancel in the form"),
    /** Remove the event from the platform. */
    REMOVE(SyncAction.DELETE, "remove"),
    /** Remove one event through its own form (e.g. a past event, which the platform's list can't reach). */
    FORM_REMOVE(SyncAction.DELETE, "remove in the form"),
    /**
     * Bring a cancelled event back: remove the cancelled copy and create it again (for a
     * platform that can't un-cancel). The engine builds it from the platform's remove and create steps.
     */
    RECREATE(SyncAction.REACTIVATE, "remove and create again");

    private final SyncAction action;
    private final String label;

    StepType(SyncAction action, String label) {
        this.action = action;
        this.label = label;
    }

    public SyncAction action() {
        return action;
    }

    /** How the step reads in the sync log ("bulk edit: refused …"). */
    public String label() {
        return label;
    }
}
