package sk.drabikp.gigsynchub.accounts.application;

import org.jmolecules.architecture.hexagonal.Application;
import sk.drabikp.gigsynchub.accounts.application.port.in.AccountsErrorCode;
import sk.drabikp.gigsynchub.accounts.application.port.in.AuditUseCase;
import sk.drabikp.gigsynchub.accounts.application.port.out.AuditStore;
import sk.drabikp.gigsynchub.accounts.application.port.out.MembershipStore;
import sk.drabikp.gigsynchub.accounts.application.port.out.UserStore;
import sk.drabikp.gigsynchub.accounts.domain.AuditEvent;
import sk.drabikp.gigsynchub.accounts.domain.Membership;
import sk.drabikp.gigsynchub.accounts.domain.User;
import sk.drabikp.gigsynchub.accounts.domain.UserId;
import sk.drabikp.gigsynchub.gig.application.NotFoundException;
import sk.drabikp.gigsynchub.gig.application.UserFacingException;
import sk.drabikp.gigsynchub.gig.domain.BandId;

import java.util.List;
import java.util.Map;

/** The bands' audit logs: every change made through the API, read by the band's owners. */
@Application
public class AuditService implements AuditUseCase {

    private final AuditStore store;
    private final MembershipStore memberships;
    private final UserStore users;

    public AuditService(AuditStore store, MembershipStore memberships, UserStore users) {
        this.store = store;
        this.memberships = memberships;
        this.users = users;
    }

    @Override
    public void record(AuditEvent event) {
        store.add(event);
    }

    @Override
    public List<Entry> recent(UserId owner, BandId band, int limit) {
        Membership asker = memberships.of(owner, band).orElseThrow(() -> new NotFoundException("No such band."));
        if (!asker.role().mayChangeTheBand()) {
            throw new UserFacingException(AccountsErrorCode.OWNER_ONLY, Map.of(), "Only the band's owners may see this.");
        }
        return store.recent(band, Math.max(1, Math.min(limit, 500))).stream()
                .map(event -> new Entry(event, event.user() == null ? null : who(event.user(), band)))
                .toList();
    }

    private String who(UserId user, BandId band) {
        String inBand = memberships.of(user, band).map(Membership::displayName).orElse(null);
        return inBand != null ? inBand : users.byId(user).map(User::shownAs).orElse(null);
    }
}
