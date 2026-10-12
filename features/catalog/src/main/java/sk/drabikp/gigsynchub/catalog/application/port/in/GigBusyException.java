package sk.drabikp.gigsynchub.catalog.application.port.in;

import sk.drabikp.gigsynchub.gig.application.UserFacingException;

import java.util.Map;

/** The gig's platform work is running right now; changing the gig must wait until it is done. */
public class GigBusyException extends UserFacingException {

    public GigBusyException(String gigLabel) {
        super(CatalogErrorCode.GIG_BUSY, Map.of("gig", gigLabel), gigLabel + ": its platform work is running right now — try again when it is done.");
    }
}
