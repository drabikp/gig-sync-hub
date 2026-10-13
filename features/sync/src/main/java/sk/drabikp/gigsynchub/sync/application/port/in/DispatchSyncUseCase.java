package sk.drabikp.gigsynchub.sync.application.port.in;

/** Driving the sync outbox — for the background worker. One caller at a time. */
public interface DispatchSyncUseCase {

    /**
      * Runs the next workload that may run — the band's whole due work of one action on one
      * platform, through its workflow; false when nothing is due.
      */
    boolean runNext();

    /** After a restart: settles the tasks that were running when the app stopped. */
    void recoverInterrupted();

    /**
     * Settles tasks left RUNNING by something other than a restart — no worker holds their lane
     * and nothing has been written to them for a while. A RUNNING task is invisible to the worker
     * and makes its gig busy, so one of them used to mean a gig nobody could touch until the app
     * was restarted.
     */
    void reapStranded();
}
