package sk.drabikp.gigsynchub.accounts.application.port.out;

import org.jmolecules.ddd.annotation.Repository;
import sk.drabikp.gigsynchub.accounts.domain.AuditEvent;
import java.time.Instant;
import sk.drabikp.gigsynchub.gig.domain.BandId;

import java.util.List;

/** The bands' audit logs. */
@Repository
public interface AuditStore {

    void add(AuditEvent event);

    /** The band's newest events first. */
    List<AuditEvent> recent(BandId band, int limit);

    /**
     * Forgets what happened before {@code when}; answers how many rows went. One row per change
     * every member makes, kept for ever, is a table that only grows — and a record of who did what,
     * which is not something to keep longer than it is useful.
     */
    int forgetBefore(Instant when);
}
