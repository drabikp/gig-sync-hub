package sk.drabikp.gigsynchub.accounts.application;

import org.jmolecules.architecture.hexagonal.Application;
import sk.drabikp.gigsynchub.accounts.application.port.in.AccountsErrorCode;
import sk.drabikp.gigsynchub.accounts.application.port.in.BandAccess;
import sk.drabikp.gigsynchub.accounts.application.port.in.BandDirectory;
import sk.drabikp.gigsynchub.accounts.application.port.in.BandsUseCase;
import sk.drabikp.gigsynchub.accounts.application.port.in.UsersUseCase;
import sk.drabikp.gigsynchub.accounts.application.port.out.BandStore;
import sk.drabikp.gigsynchub.accounts.application.port.out.CurrentLogin;
import sk.drabikp.gigsynchub.accounts.application.port.out.MembershipStore;
import sk.drabikp.gigsynchub.accounts.application.port.out.UserStore;
import sk.drabikp.gigsynchub.accounts.domain.Band;
import sk.drabikp.gigsynchub.accounts.domain.BandMembers;
import sk.drabikp.gigsynchub.accounts.domain.Membership;
import sk.drabikp.gigsynchub.accounts.domain.Role;
import sk.drabikp.gigsynchub.accounts.domain.User;
import sk.drabikp.gigsynchub.accounts.domain.UserId;
import sk.drabikp.gigsynchub.gig.application.NotFoundException;
import sk.drabikp.gigsynchub.gig.application.UserFacingException;
import sk.drabikp.gigsynchub.gig.application.port.out.Transactions;
import sk.drabikp.gigsynchub.gig.domain.BandId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Users, bands and memberships. A user is made at the first sign-in from what the sign-in says
 * ({@link CurrentLogin}); the band the app ran for before it knew bands ({@link BandId#FIRST})
 * gets its first owner then: the user with {@code firstOwnerEmail}, or — when that isn't set —
 * the first one to sign in.
 */
@Application
public class AccountsService implements UsersUseCase, BandsUseCase, BandAccess, BandDirectory {

    /** The name and address migration V2 gave the first band, until it is named. */
    static final String FIRST_SLUG = "first";
    static final String FIRST_NAME = "My band";

    private final CurrentLogin login;
    private final UserStore users;
    private final BandStore bands;
    private final MembershipStore memberships;
    private final Transactions transactions;
    private final String firstOwnerEmail;
    private final int maxBandsOwned;

    public AccountsService(CurrentLogin login, UserStore users, BandStore bands, MembershipStore memberships,
                           Transactions transactions, String firstOwnerEmail, int maxBandsOwned) {
        this.login = login;
        this.users = users;
        this.bands = bands;
        this.memberships = memberships;
        this.transactions = transactions;
        this.firstOwnerEmail = firstOwnerEmail == null ? "" : firstOwnerEmail.strip();
        this.maxBandsOwned = maxBandsOwned;
    }

    @Override
    public Optional<User> current() {
        return login.current().map(signedIn -> transactions.computeInTransaction(() -> {
            User user = users.bySubject(signedIn.subject()).orElseGet(() -> {
                User made = new User(new UserId(UUID.randomUUID()), signedIn.subject(), signedIn.email(),
                        signedIn.name());
                users.save(made);
                return made;
            });
            if (!Objects.equals(user.email(), signedIn.email()) || !Objects.equals(user.displayName(), signedIn.name())) {
                user = new User(user.id(), user.subject(), signedIn.email(), signedIn.name());   // as the sign-in says now
                users.save(user);
            }
            claimFirstBand(user);
            return user;
        }));
    }

    /**
     * The first band without an owner goes to the owner the configuration NAMES, and to nobody
     * otherwise.
     *
     * <p>It used to go to whoever signed in first when no e-mail was named — and the property is
     * commented out by default, so that was the default. Band #1 is the band migration V2 gave
     * every pre-existing row: its whole gig history, and after adoption the way back in to every
     * platform it was connected to. Anyone in the sign-in's group who reached the app first
     * acquired all of it, once, with nothing to undo it (the lastOwner rule then keeps the band
     * from becoming ownerless again). A one-shot land grab at install time is not a thing to leave
     * to who clicks first, so with nobody named this does nothing.
     */
    private void claimFirstBand(User user) {
        if (bands.byId(BandId.FIRST).isEmpty() || memberships.ofBand(BandId.FIRST).hasOwner()) {
            return;
        }
        if (firstOwnerEmail.isEmpty()) {
            return;        // nobody named, so nobody claims it: set gigsynchub.accounts.first-owner-email
        }
        if (firstOwnerEmail.equalsIgnoreCase(user.email())) {
            memberships.save(new Membership(user.id(), BandId.FIRST, Role.OWNER, null));
        }
    }

    @Override
    public List<MyBand> myBands(UserId user) {
        List<MyBand> mine = new ArrayList<>();
        for (Membership m : memberships.ofUser(user)) {
            bands.byId(m.band()).ifPresent(band -> mine.add(new MyBand(band, m.role(), m.displayName())));
        }
        mine.sort(Comparator.comparing(b -> b.band().name().toLowerCase(Locale.ROOT)));
        return mine;
    }

    @Override
    public MyBand band(UserId user, BandId band) {
        Membership membership = memberships.of(user, band).orElseThrow(() -> new NotFoundException("No such band."));
        Band found = bands.byId(band).orElseThrow(() -> new NotFoundException("No such band."));
        return new MyBand(found, membership.role(), membership.displayName());
    }

    @Override
    public MyBand create(UserId user, String name, String slug) {
        return transactions.computeInTransaction(() -> {
            Band band = valid(new BandId(UUID.randomUUID()), name, slug);
            requireFree(band.slug(), band.id());
            // The quota is counted and then added to, so it is held first — two "Create your band"
            // clicks at the same moment both counted nine and both made the tenth.
            users.hold(user);
            long owned = memberships.ofUser(user).stream().filter(m -> m.role() == Role.OWNER).count();
            if (owned >= maxBandsOwned) {
                throw new UserFacingException(AccountsErrorCode.TOO_MANY_BANDS,
                        Map.of("max", String.valueOf(maxBandsOwned)), "One may own at most " + maxBandsOwned + " bands.");
            }
            bands.save(band);
            memberships.save(new Membership(user, band.id(), Role.OWNER, null));
            return new MyBand(band, Role.OWNER, null);
        });
    }

    @Override
    public MyBand update(UserId user, BandId band, String name, String slug, Boolean publicList) {
        return transactions.computeInTransaction(() -> {
            MyBand mine = band(user, band);
            if (!mine.role().mayChangeTheBand()) {
                throw new UserFacingException(AccountsErrorCode.OWNER_ONLY, Map.of(),
                        "Only the band's owners may change it.");
            }
            Band renamed = valid(band, name, slug)
                    .listedPublicly(publicList != null ? publicList : mine.band().publicList());
            requireFree(renamed.slug(), band);
            bands.save(renamed);
            return new MyBand(renamed, mine.role(), mine.displayName());
        });
    }

    /** Names the first band from the configuration while it still has the name migration V2 gave it. */
    public void nameFirstBand(String slug, String name) {
        transactions.inTransaction(() -> bands.byId(BandId.FIRST)
                .filter(first -> FIRST_SLUG.equals(first.slug()) && FIRST_NAME.equals(first.name()))
                .ifPresent(first -> {
                    String newSlug = Band.validSlug(slug) && bands.bySlug(slug).isEmpty() ? slug : first.slug();
                    String newName = Band.validName(name) ? name : first.name();
                    bands.save(first.renamed(newName, newSlug));
                }));
    }

    @Override
    public void deleteMe(UserId user) {
        transactions.inTransaction(() -> {
            List<BandId> alone = new ArrayList<>();
            // Held in one agreed order, because two people deleting their accounts at the same moment
            // are holding the rosters of every band they share: unordered, one takes A then waits for
            // B while the other holds B and waits for A, and PostgreSQL ends the deadlock by failing
            // one of them with a 500 for what the design says is a safe thing to do.
            List<Membership> mineInOrder = memberships.ofUser(user).stream()
                    .sorted(Comparator.comparing(m -> m.band().value()))
                    .toList();
            for (Membership mine : mineInOrder) {
                memberships.hold(mine.band());                  // decide on a roster nobody else is changing
                BandMembers roster = memberships.ofBand(mine.band());
                if (roster.onlyMember(user)) {
                    alone.add(mine.band());
                } else if (!roster.without(user).hasOwner()) {
                    String name = bands.byId(mine.band()).map(Band::name).orElse("");
                    throw new UserFacingException(AccountsErrorCode.LAST_OWNER, Map.of("band", name),
                            "You're the only owner of " + name + " — make another member an owner first.");
                }
            }
            alone.forEach(bands::delete);
            users.delete(user);
        });
    }

    @Override
    public void delete(UserId owner, BandId band, String confirmSlug) {
        transactions.inTransaction(() -> {
            MyBand mine = band(owner, band);
            if (!mine.role().mayChangeTheBand()) {
                throw new UserFacingException(AccountsErrorCode.OWNER_ONLY, Map.of(),
                        "Only the band's owners may delete it.");
            }
            if (!mine.band().slug().equals(confirmSlug == null ? null : confirmSlug.strip())) {
                throw new UserFacingException(AccountsErrorCode.CONFIRM_DELETE, Map.of("slug", mine.band().slug()),
                        "Type the band's address to delete it.");
            }
            bands.delete(band);
        });
    }

    @Override
    public Optional<Role> role(UserId user, BandId band) {
        return memberships.of(user, band).map(Membership::role);
    }

    @Override
    public List<BandId> all() {
        return bands.all().stream().map(Band::id).toList();
    }

    @Override
    public Optional<Band> bySlug(String slug) {
        return bands.bySlug(slug);
    }

    private static Band valid(BandId id, String name, String slug) {
        List<String> wrong = new ArrayList<>();
        if (!Band.validName(name)) {
            wrong.add("name");
        }
        if (!Band.validSlug(slug)) {
            wrong.add("slug");
        }
        if (!wrong.isEmpty()) {
            throw new UserFacingException(AccountsErrorCode.INVALID_BAND, Map.of("fields", String.join(",", wrong)),
                    "Check the band's " + String.join(" and ", wrong) + ".");
        }
        return new Band(id, slug, name);
    }

    private void requireFree(String slug, BandId band) {
        bands.bySlug(slug).filter(other -> !other.id().equals(band)).ifPresent(other -> {
            throw new UserFacingException(AccountsErrorCode.SLUG_TAKEN, Map.of("slug", slug),
                    "Another band has the address " + slug + ".");
        });
    }
}
