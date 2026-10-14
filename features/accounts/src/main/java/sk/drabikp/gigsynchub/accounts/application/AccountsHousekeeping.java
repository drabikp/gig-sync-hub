package sk.drabikp.gigsynchub.accounts.application;

import org.jmolecules.architecture.hexagonal.Application;
import sk.drabikp.gigsynchub.accounts.application.port.in.ForgetOldRecordsUseCase;
import sk.drabikp.gigsynchub.accounts.application.port.out.AuditStore;
import sk.drabikp.gigsynchub.accounts.application.port.out.InvitationStore;

import java.time.Instant;

/** {@link ForgetOldRecordsUseCase}: the two accounts tables that nothing used to bound. */
@Application
public class AccountsHousekeeping implements ForgetOldRecordsUseCase {

    private final AuditStore audit;
    private final InvitationStore invitations;

    public AccountsHousekeeping(AuditStore audit, InvitationStore invitations) {
        this.audit = audit;
        this.invitations = invitations;
    }

    @Override
    public int forgetAuditBefore(Instant when) {
        return audit.forgetBefore(when);
    }

    @Override
    public int forgetExpiredInvitationsBefore(Instant when) {
        return invitations.forgetExpiredBefore(when);
    }
}
