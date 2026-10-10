package sk.drabikp.bzscraper.check.application.port.in;

import sk.drabikp.bzscraper.gig.domain.BandId;

/** Starts a band's platform check in the background (it reads the platforms — slow); nothing when one runs. */
public interface StartPlatformCheckUseCase {

    void start(BandId band);
}
