package sk.drabikp.gigsynchub.sync.application;

import org.jmolecules.architecture.hexagonal.Application;
import sk.drabikp.gigsynchub.gig.application.port.out.LiveUpdates;
import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.sync.application.port.out.SyncNotifier;

/**
 * Passes "the sync changed" from the engine and the use cases on to the open pages. A gig's
 * platform state is part of the catalog's gigs too, so they are read again as well.
 */
@Application
public class SyncChanges implements SyncNotifier {

    private final LiveUpdates live;

    public SyncChanges(LiveUpdates live) {
        this.live = live;
    }

    @Override
    public void changed(BandId band) {
        live.changed(band, LiveUpdates.Topic.SYNC);
        live.changed(band, LiveUpdates.Topic.GIGS);
    }

    @Override
    public void changedEverywhere() {
        live.changedEverywhere(LiveUpdates.Topic.SYNC);
    }
}
