package sk.drabikp.gigsynchub.sync.application.port.in;

/** Tells whoever runs the platform work (the sync worker) that new work was queued. */
public interface SyncWorkSignal {

    void onWork(Runnable listener);
}
