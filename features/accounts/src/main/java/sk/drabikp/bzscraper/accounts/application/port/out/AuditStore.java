package sk.drabikp.bzscraper.accounts.application.port.out;

import sk.drabikp.bzscraper.accounts.domain.AuditEvent;
import sk.drabikp.bzscraper.gig.domain.BandId;

import java.util.List;

/** The bands' audit logs. */
public interface AuditStore {

    void add(AuditEvent event);

    /** The band's newest events first. */
    List<AuditEvent> recent(BandId band, int limit);
}
