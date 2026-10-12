package sk.drabikp.gigsynchub.importing.application.port.in;

import sk.drabikp.gigsynchub.gig.application.ErrorCode;

/** What Import says no with. */
public enum ImportErrorCode implements ErrorCode {

    /** No platform was picked to read. */
    NO_PLATFORM("noPlatform"),
    /** Nothing was read yet to import. */
    NO_PLAN("noPlan"),
    /** The plan was applied with nothing chosen. */
    NOTHING_SELECTED("nothingSelected"),
    /** The platforms are being read again: the plan decided on is about to be replaced. */
    IMPORT_READING("importReading");

    private final String code;

    ImportErrorCode(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
