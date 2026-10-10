package sk.drabikp.bzscraper.sync.application.engine;

import sk.drabikp.bzscraper.sync.domain.Lane;

/**
 * How each band's account on each platform has been answering lately — the engine reports, and skips
 * the lanes held back (a band's account, or a whole platform that seems down for everyone).
 */
public interface PlatformHealth {

    /** The lane's work is held back right now (its own breaker, or its platform's). */
    boolean held(Lane lane);

    /** The platform answered the band's account (any result other than a temporary failure). */
    void succeeded(Lane lane);

    /** A batch of the band's on the platform failed as a whole. */
    void failed(Lane lane, String failure);
}
