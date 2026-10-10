package sk.drabikp.bzscraper.sync.application.port.out;

/**
 * Where a step's unexpected failure goes in full — a fault of the app (a page that changed, a
 * bug), not an answer of the platform. The task itself fails with a short line; this keeps the
 * kind of failure and where it happened for whoever fixes it.
 */
public interface SyncFaults {

    void unexpected(SyncStep step, RuntimeException failure);
}
