package sk.drabikp.bzscraper.sync.domain;

import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;

import java.util.Objects;

/**
 * A band's work on one platform — done with the band's account there, so one run at a time: the sync
 * never runs two of a lane's runs at once, and a lane's breaker holds back only that band.
 */
public record Lane(BandId band, Platform platform) {

    public Lane {
        Objects.requireNonNull(band, "the band");
        Objects.requireNonNull(platform, "the platform");
    }

    public static Lane of(SyncTask task) {
        return new Lane(task.band(), task.platform());
    }
}
