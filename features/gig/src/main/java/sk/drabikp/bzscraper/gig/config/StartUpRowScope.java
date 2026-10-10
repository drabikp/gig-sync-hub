package sk.drabikp.bzscraper.gig.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.gig.application.port.out.RowScope;

/**
 * The app's start-up work (the runners: band #1's accounts and calendar kept, the shipped rules, keys
 * rotated) is the app's own across bands: every band's rows from the moment the app has started until it is
 * ready — on the thread that starts it; nothing else.
 */
@Component
class StartUpRowScope {

    private final RowScope rows;
    private RowScope.Scope startUp;

    StartUpRowScope(RowScope rows) {
        this.rows = rows;
    }

    @EventListener(ApplicationStartedEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    void started() {
        startUp = rows.everyBand();
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    void ready() {
        if (startUp != null) {
            startUp.close();
            startUp = null;
        }
    }
}
