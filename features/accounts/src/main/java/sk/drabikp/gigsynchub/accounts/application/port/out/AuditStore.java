package sk.drabikp.gigsynchub.accounts.application.port.out;

import org.jmolecules.ddd.annotation.Repository;
import sk.drabikp.gigsynchub.accounts.domain.AuditEvent;
import sk.drabikp.gigsynchub.gig.domain.BandId;

import java.util.List;

/** The bands' audit logs. */
@Repository
public interface AuditStore {

    void add(AuditEvent event);

    /** The band's newest events first. */
    List<AuditEvent> recent(BandId band, int limit);
}
