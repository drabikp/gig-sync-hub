package sk.drabikp.gigsynchub.sync.application.port.out;

/** Wakes the sync worker: tasks were queued (it also checks periodically, so a lost wake only delays). */
public interface SyncTrigger {

    void wake();
}
