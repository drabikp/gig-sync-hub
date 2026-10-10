package sk.drabikp.bzscraper.accounts.application;

import sk.drabikp.bzscraper.accounts.application.port.in.AccountsErrorCode;
import sk.drabikp.bzscraper.accounts.application.port.in.BandAccess;
import sk.drabikp.bzscraper.accounts.application.port.in.PlatformAccountsUseCase;
import sk.drabikp.bzscraper.accounts.application.port.in.SecretBox;
import sk.drabikp.bzscraper.accounts.application.port.out.PlatformAccountCheck;
import sk.drabikp.bzscraper.accounts.application.port.out.PlatformAccountStore;
import sk.drabikp.bzscraper.accounts.domain.AccountCheck;
import sk.drabikp.bzscraper.accounts.domain.PlatformAccount;
import sk.drabikp.bzscraper.accounts.domain.Role;
import sk.drabikp.bzscraper.accounts.domain.UserId;
import sk.drabikp.bzscraper.gig.application.NotFoundException;
import sk.drabikp.bzscraper.gig.application.UserFacingException;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.gig.domain.platform.PlatformTraits;
import sk.drabikp.bzscraper.gig.domain.platform.Platforms;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The bands' platform accounts. What an account is made of is the platform's ({@link AccountField}s in
 * its {@link PlatformTraits}): settings for its text and yes/no fields, secrets for its secret ones —
 * anything else is refused ({@link AccountForm}). Owners connect and disconnect; any member may test the
 * sign-in.
 */
public class PlatformAccountService implements PlatformAccountsUseCase {

    private final PlatformAccountStore store;
    private final Platforms platforms;
    private final BandAccess access;
    private final SecretBox box;
    private final Supplier<List<PlatformAccountCheck>> checks;
    private final Clock clock;

    public PlatformAccountService(PlatformAccountStore store, Platforms platforms, BandAccess access, SecretBox box,
                                  Supplier<List<PlatformAccountCheck>> checks, Clock clock) {
        this.store = store;
        this.platforms = platforms;
        this.access = access;
        this.box = box;
        this.checks = checks;
        this.clock = clock;
    }

    @Override
    public List<AccountView> accounts(UserId user, BandId band) {
        member(user, band);
        Map<Platform, PlatformAccount> connected = store.ofBand(band).stream()
                .collect(Collectors.toMap(PlatformAccount::platform, Function.identity()));
        return platforms.traits().stream()
                .filter(traits -> !traits.accountFields().isEmpty())
                .map(traits -> view(traits.platform(), connected.get(traits.platform())))
                .toList();
    }

    @Override
    public AccountView connect(UserId user, BandId band, Platform platform, Map<String, String> settings,
                               Map<String, String> secrets) {
        owner(user, band);
        PlatformTraits traits = withAccount(platform);
        PlatformAccount kept = store.of(band, platform).orElse(null);
        PlatformAccount account = AccountForm.valid(band, traits, settings, secrets, kept, box);
        store.save(account);
        return view(platform, account);
    }

    @Override
    public void disconnect(UserId user, BandId band, Platform platform) {
        owner(user, band);
        withAccount(platform);
        store.delete(band, platform);
    }

    @Override
    public AccountView check(UserId user, BandId band, Platform platform) {
        member(user, band);
        withAccount(platform);
        store.of(band, platform)
                .orElseThrow(() -> new NotFoundException(platforms.name(platform) + " isn't connected for this band."));
        Optional<PlatformAccountCheck> check = checks.get().stream().filter(c -> c.platform().equals(platform))
                .findFirst();
        AccountCheck.Outcome outcome;
        if (check.isEmpty()) {
            outcome = AccountCheck.Outcome.failed("This server can't sign in to " + platforms.name(platform) + ".");
        } else {
            try {
                outcome = check.get().check(band);
            } catch (RuntimeException e) {
                outcome = AccountCheck.Outcome.failed(e.getMessage());
            }
        }
        // the sign-in took a while: the result goes on what is saved now (nothing, when it was disconnected)
        AccountCheck dated = outcome.at(clock.instant());
        Optional<PlatformAccount> checked = store.of(band, platform).map(now -> now.checked(dated));
        checked.ifPresent(store::save);
        return view(platform, checked.orElse(null));
    }

    private AccountView view(Platform platform, PlatformAccount account) {
        return account == null ? new AccountView(platform, false, Map.of(), Set.of(), null)
                : new AccountView(platform, true, account.settings(), new TreeSet<>(account.secrets().keySet()),
                        account.lastCheck());
    }

    private PlatformTraits withAccount(Platform platform) {
        PlatformTraits traits = platforms.traits(platform);
        if (traits.accountFields().isEmpty()) {
            throw new NotFoundException(traits.displayName() + " has no account to connect.");
        }
        return traits;
    }

    private Role member(UserId user, BandId band) {
        return access.role(user, band).orElseThrow(() -> new NotFoundException("No such band."));
    }

    private void owner(UserId user, BandId band) {
        if (!member(user, band).mayChangeTheBand()) {
            throw new UserFacingException(AccountsErrorCode.OWNER_ONLY, Map.of(),
                    "Only the band's owners may connect its platforms.");
        }
    }
}
