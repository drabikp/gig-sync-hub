package sk.drabikp.gigsynchub.check.application.port.in;

import sk.drabikp.gigsynchub.gig.domain.BandId;

/** Starts a band's platform check in the background (it reads the platforms — slow); nothing when one runs. */
public interface StartPlatformCheckUseCase {

    void start(BandId band);
}
