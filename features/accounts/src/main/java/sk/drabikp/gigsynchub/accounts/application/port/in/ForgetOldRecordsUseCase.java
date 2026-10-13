package sk.drabikp.gigsynchub.accounts.application.port.in;

import org.jmolecules.architecture.hexagonal.PrimaryPort;

import java.time.Instant;

/**
 * Letting go of records that have done their job: the audit log, and invitations that ran out. Both
 * only ever grew — one audit row per change every member makes, one invitation row per link an owner
 * ever sent — and both hold something about a person (who did what; an e-mail address), so keeping
 * them for ever is a cost twice over.
 */
@PrimaryPort
public interface ForgetOldRecordsUseCase {

    /** Forgets audit events from before {@code when}; answers how many went. */
    int forgetAuditBefore(Instant when);

    /** Forgets invitations that ran out before {@code when}; answers how many went. */
    int forgetExpiredInvitationsBefore(Instant when);
}
