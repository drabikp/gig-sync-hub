package sk.drabikp.gigsynchub.accounts.application.port.in;

import sk.drabikp.gigsynchub.accounts.domain.AuditEvent;
import sk.drabikp.gigsynchub.accounts.domain.UserId;
import sk.drabikp.gigsynchub.gig.domain.BandId;

import java.util.List;

/** Who changed what in a band: written for every change made through the API, read by the band's owners. */
public interface AuditUseCase {

    /** An event as owners read it: who made it ({@code who}: their name in the band, null when they're gone). */
    record Entry(AuditEvent event, String who) {
    }

    void record(AuditEvent event);

    /** The band's latest changes, newest first (owners only). */
    List<Entry> recent(UserId owner, BandId band, int limit);
}
