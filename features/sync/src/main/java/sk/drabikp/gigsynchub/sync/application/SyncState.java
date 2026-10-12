package sk.drabikp.gigsynchub.sync.application;

import org.jmolecules.architecture.hexagonal.Application;
import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.gig.domain.GigId;
import sk.drabikp.gigsynchub.gig.domain.platform.Platform;
import sk.drabikp.gigsynchub.sync.application.engine.PlatformHealth;
import sk.drabikp.gigsynchub.sync.application.port.in.SyncStateUseCase;
import sk.drabikp.gigsynchub.sync.application.port.out.SyncTaskQueries;
import sk.drabikp.gigsynchub.sync.domain.Lane;
import sk.drabikp.gigsynchub.sync.domain.SyncStatus;
import sk.drabikp.gigsynchub.sync.domain.SyncTask;

import java.util.Set;
import java.util.stream.Collectors;

@Application
public class SyncState implements SyncStateUseCase {

    private final SyncTaskQueries outbox;
    private final PlatformHealth health;

    public SyncState(SyncTaskQueries outbox, PlatformHealth health) {
        this.outbox = outbox;
        this.health = health;
    }

    @Override
    public boolean busy(BandId band, GigId gigId) {
        return outbox.openFor(band, gigId).stream().anyMatch(t -> t.status() == SyncStatus.RUNNING);
    }

    @Override
    public Set<GigId> gigsWithOpenWork(BandId band) {
        return outbox.unfinished(band).stream().filter(t -> t.status().open()).map(SyncTask::gigId)
                .collect(Collectors.toSet());
    }

    @Override
    public boolean heldBack(BandId band, Platform platform) {
        return health.held(new Lane(band, platform));
    }
}
