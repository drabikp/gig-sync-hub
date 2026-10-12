package sk.drabikp.gigsynchub.sync.application.port.out;

import sk.drabikp.gigsynchub.sync.application.SyncFakes;

/** The service tests' in-memory outbox keeps the outbox's contract. */
class FakeOutboxContractTest extends SyncOutboxContract {

    private final SyncFakes.Outbox outbox = new SyncFakes.Outbox();

    @Override
    protected SyncOutbox outbox() {
        return outbox;
    }
}
