package sk.drabikp.gigsynchub.sync.application;

import org.jmolecules.architecture.hexagonal.Application;
import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.sync.application.port.in.PauseSyncUseCase;
import sk.drabikp.gigsynchub.sync.application.port.out.SettingsStore;
import sk.drabikp.gigsynchub.sync.application.port.out.SyncNotifier;
import sk.drabikp.gigsynchub.sync.application.port.out.SyncTrigger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Each band's pause switch: read by the dispatcher and the engine before each piece of work. It
 * survives a restart — paused stays paused until the band's user resumes.
 */
@Application
public class SyncPause implements PauseSyncUseCase {

    static final String SETTING = "sync.paused";

    private final SyncTrigger trigger;
    private final SyncNotifier notifier;
    private final SettingsStore settings;
    private final Set<BandId> paused = ConcurrentHashMap.newKeySet();

    public SyncPause(SyncTrigger trigger, SyncNotifier notifier, SettingsStore settings) {
        this.trigger = trigger;
        this.notifier = notifier;
        this.settings = settings;
        settings.all(SETTING).forEach((band, value) -> {
            if (Boolean.parseBoolean(value)) {
                paused.add(band);
            }
        });
    }

    @Override
    public void pause(BandId band) {
        settings.put(band, SETTING, "true");
        paused.add(band);
        notifier.changed(band);
    }

    @Override
    public void resume(BandId band) {
        settings.put(band, SETTING, null);
        paused.remove(band);
        notifier.changed(band);
        trigger.wake();
    }

    @Override
    public boolean paused(BandId band) {
        return paused.contains(band);
    }

    @Override
    public Set<BandId> pausedBands() {
        return Set.copyOf(paused);
    }
}
