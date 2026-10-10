package sk.drabikp.bzscraper.gig.application;

import java.util.Map;

/**
 * The data was changed by someone else since it was read — another window, an import, the
 * calendar — so the change was NOT made (optimistic locking: nothing is locked while the user
 * edits; a stale edit is refused when it is saved). Reload and do it again.
 */
public class ConcurrentChangeException extends UserFacingException {

    private ConcurrentChangeException(GigErrorCode code, String message, Throwable cause) {
        super(code, Map.of(), message, cause);
    }

    /** The gig is not as the user saw it. */
    public static ConcurrentChangeException changed() {
        return changed(null);
    }

    /** The gig is not as the user saw it: another transaction saved it at the same time ({@code cause}). */
    public static ConcurrentChangeException changed(Throwable cause) {
        return new ConcurrentChangeException(GigErrorCode.CHANGED_MEANWHILE, "The gig was changed meanwhile (in another window, "
                + "by an import or from the calendar) — nothing was changed. Reload it and edit again.", cause);
    }

    /** The gig is gone. */
    public static ConcurrentChangeException deleted() {
        return new ConcurrentChangeException(GigErrorCode.DELETED_MEANWHILE, "The gig was deleted meanwhile — nothing was changed.",
                null);
    }
}
