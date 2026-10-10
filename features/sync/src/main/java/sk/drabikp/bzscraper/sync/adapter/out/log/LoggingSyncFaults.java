package sk.drabikp.bzscraper.sync.adapter.out.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.sync.application.port.out.SyncFaults;
import sk.drabikp.bzscraper.sync.application.port.out.SyncStep;

/** {@link SyncFaults} in the app's log, with the stack trace. */
@Component
class LoggingSyncFaults implements SyncFaults {

    private static final Logger log = LoggerFactory.getLogger(LoggingSyncFaults.class);

    @Override
    public void unexpected(SyncStep step, RuntimeException failure) {
        log.error("{} on {} failed unexpectedly", step.type().label(), step.platform(), failure);
    }
}
