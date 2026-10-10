package sk.drabikp.bzscraper.accounts.application;

import sk.drabikp.bzscraper.accounts.application.port.in.AccountsErrorCode;
import sk.drabikp.bzscraper.accounts.application.port.in.BandAccess;
import sk.drabikp.bzscraper.accounts.application.port.in.BandDirectory;
import sk.drabikp.bzscraper.accounts.application.port.in.BandsUseCase;
import sk.drabikp.bzscraper.accounts.application.port.in.MembersUseCase;
import sk.drabikp.bzscraper.accounts.application.port.in.UsersUseCase;
import sk.drabikp.bzscraper.accounts.application.port.out.BandStore;
import sk.drabikp.bzscraper.accounts.application.port.out.CurrentLogin;
import sk.drabikp.bzscraper.accounts.application.port.out.MembershipStore;
import sk.drabikp.bzscraper.accounts.application.port.out.UserStore;
import sk.drabikp.bzscraper.accounts.domain.Band;
import sk.drabikp.bzscraper.accounts.domain.Membership;
import sk.drabikp.bzscraper.accounts.domain.Role;
import sk.drabikp.bzscraper.accounts.domain.User;
import sk.drabikp.bzscraper.accounts.domain.UserId;
import sk.drabikp.bzscraper.gig.application.NotFoundException;
import sk.drabikp.bzscraper.gig.application.UserFacingException;
import sk.drabikp.bzscraper.gig.application.port.out.Transactions;
import sk.drabikp.bzscraper.gig.domain.BandId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Users, bands and memberships. A user is made at the first sign-in from what the sign-in says
 * ({@link CurrentLogin}); the band the app ran for before it knew bands ({@link BandId#FIRST})
 * gets its first owner then: the user with {@code firstOwnerEmail}, or — when that isn't set —
 * the first one to sign in.
 */
public class AccountsService implements UsersUseCase, BandsUseCase, MembersUseCase, BandAccess, BandDirectory {

    /** The name and address migration V2 gave the first band, until it is named. */
    static final String FIRST_SLUG = "first";
    static final String FIRST_NAME = "My band";

    private final CurrentLogin login;
    private final UserStore users;
    private final BandStore bands;
    private final MembershipStore memberships;
    private final Transactions transactions;
    private final String firstOwnerEmail;

    public AccountsService(CurrentLogin login, UserStore users, BandStore bands, MembershipStore memberships,
                           Transactions transactions, String firstOwnerEmail) {
        this.login = login;
        this.users = users;
        this.bands = bands;
        this.memberships = memberships;
        this.transactions = transactions;
        this.firstOwnerEmail = firstOwnerEmail == null ? "" : firstOwnerEmail.strip();
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
            claimFirstBand(user);
            return user;
        }));
    }

    /** The first band without an owner goes to its owner-to-be (or, with none named, to whoever is first). */
    private void claimFirstBand(User user) {
        boolean ownerless = bands.byId(BandId.FIRST).isPresent() && memberships.ofBand(BandId.FIRST).stream()
                .noneMatch(m -> m.role() == Role.OWNER);
        boolean meant = firstOwnerEmail.isEmpty() || firstOwnerEmail.equalsIgnoreCase(user.email());
        if (ownerless && meant) {
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
            bands.save(band);
            memberships.save(new Membership(user, band.id(), Role.OWNER, null));
            return new MyBand(band, Role.OWNER, null);
        });
    }

    @Override
    public MyBand update(UserId user, BandId band, String name, String slug) {
        return transactions.computeInTransaction(() -> {
            MyBand mine = band(user, band);
            if (!mine.role().mayChangeTheBand()) {
                throw new UserFacingException(AccountsErrorCode.OWNER_ONLY, Map.of(),
                        "Only the band's owners may change it.");
            }
            Band renamed = valid(band, name, slug);
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
    public List<Member> members(UserId asker, BandId band) {
        band(asker, band);                              // members only
        return memberships.ofBand(band).stream()
                .flatMap(m -> users.byId(m.user()).map(u -> new Member(u, m.role(), m.displayName())).stream())
                .sorted(Comparator.comparing((Member m) -> m.role() != Role.OWNER)
                        .thenComparing(m -> String.valueOf(m.user().shownAs())))
                .toList();
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
