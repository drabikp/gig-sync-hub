package sk.drabikp.bzscraper.sync.application.port.in;

/** Driving the sync outbox — for the background worker. One caller at a time. */
public interface DispatchSyncUseCase {

    /**
      * Runs the next workload that may run — the band's whole due work of one action on one
      * platform, through its workflow; false when nothing is due.
      */
    boolean runNext();

    /** After a restart: settles the tasks that were running when the app stopped. */
    void recoverInterrupted();
}
