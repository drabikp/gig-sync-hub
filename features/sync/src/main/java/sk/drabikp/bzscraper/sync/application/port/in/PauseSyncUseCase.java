package sk.drabikp.bzscraper.sync.application.port.in;

import sk.drabikp.bzscraper.gig.domain.BandId;

import java.util.Set;

/**
 * Pausing a band's platform sync: the gig in progress finishes, nothing new of the band starts — its
 * waiting work stays queued — until resumed. A pause is kept over a restart; other bands go on.
 */
public interface PauseSyncUseCase {

    void pause(BandId band);

    void resume(BandId band);

    boolean paused(BandId band);

    /** The bands paused right now. */
    Set<BandId> pausedBands();
}
