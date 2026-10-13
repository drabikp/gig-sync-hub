package sk.drabikp.gigsynchub.accounts.application;

import sk.drabikp.gigsynchub.accounts.application.port.in.PlatformAccounts;
import sk.drabikp.gigsynchub.accounts.application.port.out.AuditStore;
import sk.drabikp.gigsynchub.accounts.application.port.out.BandStore;
import sk.drabikp.gigsynchub.accounts.application.port.out.CurrentLogin;
import sk.drabikp.gigsynchub.accounts.application.port.out.InvitationStore;
import sk.drabikp.gigsynchub.accounts.application.port.out.MembershipStore;
import sk.drabikp.gigsynchub.accounts.application.port.out.PlatformAccountStore;
import sk.drabikp.gigsynchub.accounts.application.port.out.UserStore;
import sk.drabikp.gigsynchub.accounts.domain.AuditEvent;
import sk.drabikp.gigsynchub.accounts.domain.Band;
import sk.drabikp.gigsynchub.accounts.domain.BandMembers;
import sk.drabikp.gigsynchub.accounts.domain.Invitation;
import sk.drabikp.gigsynchub.accounts.domain.Membership;
import sk.drabikp.gigsynchub.accounts.domain.PlatformAccount;
import sk.drabikp.gigsynchub.accounts.domain.User;
import sk.drabikp.gigsynchub.accounts.domain.UserId;
import sk.drabikp.gigsynchub.gig.application.port.out.Transactions;
import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.gig.domain.platform.Platform;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** In-memory stand-ins for the accounts' ports, a service over them, a test data key — shared with other modules' tests. */
public final class AccountsFakes {

    private AccountsFakes() {
    }

    /** Who the next request is signed in as (null: nobody). */
    public static final class SignedIn implements CurrentLogin {

        private CurrentLogin.Login current;

        public SignedIn as(String subject, String email) {
            this.current = new CurrentLogin.Login(subject, email, null);
            return this;
        }

        public void signOut() {
            current = null;
        }

        @Override
        public Optional<CurrentLogin.Login> current() {
            return Optional.ofNullable(current);
        }
    }

    public static final class Stores implements UserStore, BandStore, MembershipStore {

        private final Map<String, User> users = new LinkedHashMap<>();
        private final Map<BandId, Band> bands = new LinkedHashMap<>();
        private final List<Membership> memberships = new ArrayList<>();

        /** With the first band, as migration V2 leaves it. */
        public static Stores withFirstBand() {
            Stores stores = new Stores();
            stores.save(new Band(BandId.FIRST, AccountsService.FIRST_SLUG, AccountsService.FIRST_NAME));
            return stores;
        }

        @Override
        public Optional<User> bySubject(String subject) {
            return Optional.ofNullable(users.get(subject));
        }

        @Override
        public Optional<User> byId(UserId id) {
            return users.values().stream().filter(u -> u.id().equals(id)).findFirst();
        }

        @Override
        public void save(User user) {
            users.put(user.subject(), user);
        }

        /** One thread, no rows: there is nothing here for a hold to keep anyone out of. */
        @Override
        public void hold(UserId user) {
        }

        @Override
        public void delete(UserId user) {
            users.values().removeIf(u -> u.id().equals(user));
            memberships.removeIf(m -> m.user().equals(user));
        }

        @Override
        public Optional<Band> byId(BandId id) {
            return Optional.ofNullable(bands.get(id));
        }

        @Override
        public Optional<Band> bySlug(String slug) {
            return bands.values().stream().filter(b -> b.slug().equals(slug)).findFirst();
        }

        @Override
        public List<Band> all() {
            return List.copyOf(bands.values());
        }

        @Override
        public void save(Band band) {
            bands.put(band.id(), band);
        }

        @Override
        public Optional<Membership> of(UserId user, BandId band) {
            return memberships.stream().filter(m -> m.user().equals(user) && m.band().equals(band)).findFirst();
        }

        @Override
        public List<Membership> ofUser(UserId user) {
            return memberships.stream().filter(m -> m.user().equals(user)).toList();
        }

        @Override
        public BandMembers ofBand(BandId band) {
            return new BandMembers(band, memberships.stream().filter(m -> m.band().equals(band)).toList());
        }

        /** Nothing to hold: one list, one thread. The database's store does the real holding. */
        @Override
        public void hold(BandId band) {
        }

        @Override
        public void save(Membership membership) {
            memberships.removeIf(m -> m.user().equals(membership.user()) && m.band().equals(membership.band()));
            memberships.add(membership);
        }

        @Override
        public void delete(UserId user, BandId band) {
            memberships.removeIf(m -> m.user().equals(user) && m.band().equals(band));
        }

        @Override
        public void delete(BandId band) {
            bands.remove(band);
            memberships.removeIf(m -> m.band().equals(band));
        }
    }

    /** Invitations in memory, by their token's hash. */
    public static final class Invitations implements InvitationStore {

        public final Map<String, Invitation> byHash = new LinkedHashMap<>();

        @Override
        public void save(Invitation invitation, String tokenHash) {
            byHash.put(tokenHash, invitation);
        }

        @Override
        public void save(Invitation invitation) {
            byHash.replaceAll((hash, i) -> i.id().equals(invitation.id()) ? invitation : i);
        }

        @Override
        public Optional<Invitation> byTokenHash(String tokenHash) {
            return Optional.ofNullable(byHash.get(tokenHash));
        }

        @Override
        public Optional<Invitation> of(BandId band, UUID id) {
            return byHash.values().stream().filter(i -> i.band().equals(band) && i.id().equals(id)).findFirst();
        }

        @Override
        public List<Invitation> open(BandId band, Instant now) {
            return byHash.values().stream().filter(i -> i.band().equals(band) && i.open(now)).toList().reversed();
        }

        @Override
        public void delete(UUID id) {
            byHash.values().removeIf(i -> i.id().equals(id));
        }
    }

    /** Audit events in memory. */
    public static final class Audit implements AuditStore {

        public final List<AuditEvent> events = new ArrayList<>();

        @Override
        public void add(AuditEvent event) {
            events.add(event);
        }

        @Override
        public List<AuditEvent> recent(BandId band, int limit) {
            return events.stream().filter(e -> e.band().equals(band)).toList().reversed().stream().limit(limit)
                    .toList();
        }
    }

    /** A data key for tests (never a real one): {@code id:base64} of 32 bytes. */
    public static final String TEST_KEYS = "test:" + Base64.getEncoder().encodeToString(new byte[32]);

    /**
     * The bands' platform accounts in memory: as the store, and as the platforms read them ({@code adopt} keeps
     * what it is given, unchecked).
     */
    public static final class Accounts implements PlatformAccountStore, PlatformAccounts {

        private final Map<String, PlatformAccount> accounts = new LinkedHashMap<>();

        /** Connects the band's account on the platform. */
        public Accounts with(BandId band, Platform platform, Map<String, String> settings,
                             Map<String, String> secrets) {
            save(new PlatformAccount(band, platform, settings, secrets, null));
            return this;
        }

        @Override
        public Optional<PlatformAccount> of(BandId band, Platform platform) {
            return Optional.ofNullable(accounts.get(band + "/" + platform));
        }

        @Override
        public boolean adopt(BandId band, Platform platform, Map<String, String> settings,
                             Map<String, String> secrets) {
            if (of(band, platform).isPresent()) {
                return false;
            }
            with(band, platform, settings, secrets);
            return true;
        }

        @Override
        public List<PlatformAccount> ofBand(BandId band) {
            return accounts.values().stream().filter(a -> a.band().equals(band)).toList();
        }

        @Override
        public void save(PlatformAccount account) {
            accounts.put(account.band() + "/" + account.platform(), account);
        }

        @Override
        public void delete(BandId band, Platform platform) {
            accounts.remove(band + "/" + platform);
        }

        @Override
        public int reseal() {
            return 0;
        }
    }

    /** Runs the work directly. */
    public static final Transactions DIRECT = new Transactions() {
        @Override
        public void inTransaction(Runnable work) {
            work.run();
        }

        @Override
        public <T> T computeInTransaction(Supplier<T> work) {
            return work.get();
        }

        @Override
        public void inRepeatableTransaction(Runnable work) {
            work.run();
        }

        @Override
        public <T> T computeInRepeatableTransaction(Supplier<T> work) {
            return work.get();
        }
    };

    public static AccountsService service(SignedIn login, Stores stores, String firstOwnerEmail) {
        return new AccountsService(login, stores, stores, stores, DIRECT, firstOwnerEmail, 10);
    }

    public static MembershipService memberships(Stores stores, Invitations invitations, Clock clock) {
        return new MembershipService(stores, stores, stores, invitations, DIRECT, clock);
    }
}
