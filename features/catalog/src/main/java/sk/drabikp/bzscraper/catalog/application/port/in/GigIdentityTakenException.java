package sk.drabikp.bzscraper.catalog.application.port.in;

import sk.drabikp.bzscraper.gig.application.UserFacingException;
import sk.drabikp.bzscraper.gig.domain.Gig;

import java.util.Map;

/**
 * Another catalog gig already has this identity (the same day and venue): saving would
 * overwrite it and its platform links, so nothing was changed.
 */
public class GigIdentityTakenException extends UserFacingException {

    public GigIdentityTakenException(Gig gig) {
        super(CatalogErrorCode.GIG_IDENTITY_TAKEN, Map.of("date", gig.schedule().startDate().toString(), "venue",
                gig.location().displayVenue()), "The catalog already has a gig on " + gig.schedule().startDate() + " at "
                + gig.location().displayVenue() + " — edit that one instead.");
    }
}
