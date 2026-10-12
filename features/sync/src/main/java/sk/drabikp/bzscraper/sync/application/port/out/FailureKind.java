package sk.drabikp.bzscraper.sync.application.port.out;

import sk.drabikp.bzscraper.sync.domain.StepOutcome;

/**
 * What a failure on a platform means for the sync. The adapter that saw it says which; the
 * step's outcome follows from it in one place ({@link PlatformException#outcome()}).
 */
public enum FailureKind {

    /** It may work later — a timeout, a page that didn't load, a login hiccup: this step again, later. */
    TEMPORARY,

    /**
     * The platform said no to doing it this way and nothing happened there (its validation, a
     * past event the list can't reach): another way — the workflow's next step — may do it.
     */
    REFUSED,

    /**
     * Only the user can sort it out: the platform is switched off or not configured, or the gig's
     * data has to be corrected (a town the platform doesn't know) — trying again or another way
     * won't help.
     */
    NEEDS_USER,

    /**
     * Not tried: the work never got as far as the platform, because the thread was interrupted —
     * which in practice means the app is stopping. Again a little later; no attempt used, and the
     * platform is told nothing about it, because it heard nothing. (A create is the exception its
     * adapter makes: an interrupted create may have happened all the same, so it is reported
     * {@link #TEMPORARY} with "check there".)
     */
    BUSY;

    /** The step's outcome for a failure of this kind. */
    public StepOutcome outcome(String message) {
        return switch (this) {
            case TEMPORARY -> StepOutcome.failed(message);
            case REFUSED -> StepOutcome.refused(message);
            case NEEDS_USER -> StepOutcome.failedForGood(message);
            case BUSY -> StepOutcome.postponed(message);
        };
    }
}
