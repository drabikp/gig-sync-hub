package sk.drabikp.gigsynchub.accounts.application.port.out;

import sk.drabikp.gigsynchub.accounts.domain.AccountCheck;
import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.gig.domain.platform.Platform;

/** A platform's test of a band's account there: signing in once with it. Implemented by the platform. */
public interface PlatformAccountCheck {

    Platform platform();

    /** Signs in with the band's (saved) account; what happened, in the platform's words. Never throws. */
    AccountCheck.Outcome check(BandId band);
}
