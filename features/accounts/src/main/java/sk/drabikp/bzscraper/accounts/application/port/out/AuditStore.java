package sk.drabikp.bzscraper.accounts.application.port.out;

import org.jmolecules.ddd.annotation.Repository;
import sk.drabikp.bzscraper.accounts.domain.AuditEvent;
import sk.drabikp.bzscraper.gig.domain.BandId;

import java.util.List;

/** The bands' audit logs. */
@Repository
public interface AuditStore {

    void add(AuditEvent event);

    /** The band's newest events first. */
    List<AuditEvent> recent(BandId band, int limit);
}
