package sk.drabikp.gigsynchub.sync.application;

import org.jmolecules.architecture.hexagonal.Application;
import sk.drabikp.gigsynchub.sync.application.port.in.SyncWorkSignal;
import sk.drabikp.gigsynchub.sync.application.port.out.SyncTrigger;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * "There is platform work to do" — the services ring it when they queue work; whoever runs
 * the work (the sync worker) listens. Being its own object keeps the worker out of the
 * services' wiring (no cycle).
 */
@Application
public class SyncWakeUp implements SyncTrigger, SyncWorkSignal {

    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    @Override
    public void onWork(Runnable listener) {
        listeners.add(listener);
    }

    @Override
    public void wake() {
        listeners.forEach(Runnable::run);
    }
}
