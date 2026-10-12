package sk.drabikp.gigsynchub.catalog.application.port.in;

import sk.drabikp.gigsynchub.gig.application.ErrorCode;

/** What the catalog says no with. */
public enum CatalogErrorCode implements ErrorCode {

    /** The gig's platform work is running; {@code gig} names it ({@link GigBusyException}). */
    GIG_BUSY("gigBusy"),
    /** Another gig has that day and venue; {@code date}, {@code venue} ({@link GigIdentityTakenException}). */
    GIG_IDENTITY_TAKEN("gigIdentityTaken");

    private final String code;

    CatalogErrorCode(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
