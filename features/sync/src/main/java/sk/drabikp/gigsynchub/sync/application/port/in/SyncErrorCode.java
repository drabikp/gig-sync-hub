package sk.drabikp.gigsynchub.sync.application.port.in;

import sk.drabikp.gigsynchub.gig.application.ErrorCode;

/** What the sync says no with. */
public enum SyncErrorCode implements ErrorCode {

    /** The task is no longer where the user saw it; {@code gig}, {@code status}. */
    TASK_MOVED_ON("taskMovedOn");

    private final String code;

    SyncErrorCode(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
