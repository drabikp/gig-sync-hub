package sk.drabikp.bzscraper.accounts.application;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.accounts.application.port.in.AccountsErrorCode;
import sk.drabikp.bzscraper.accounts.application.port.in.PlatformAccountsUseCase.AccountView;
import sk.drabikp.bzscraper.accounts.application.port.out.PlatformAccountCheck;
import sk.drabikp.bzscraper.accounts.domain.AccountCheck;
import sk.drabikp.bzscraper.accounts.domain.Band;
import sk.drabikp.bzscraper.accounts.domain.Membership;
import sk.drabikp.bzscraper.accounts.domain.Role;
import sk.drabikp.bzscraper.accounts.domain.UserId;
import sk.drabikp.bzscraper.gig.application.NotFoundException;
import sk.drabikp.bzscraper.gig.application.UserFacingException;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.platform.AccountField;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.gig.domain.platform.PlatformTraits;
import sk.drabikp.bzscraper.gig.domain.platform.Platforms;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformAccountServiceTest {

    private static final Platform ALPHA = Platform.of("ALPHA");
    private static final Platform PLAIN = Platform.of("PLAIN");
    private static final Platforms PLATFORMS = new Platforms(List.of(
            PlatformTraits.of(ALPHA, "Alpha")
                    .accountField(AccountField.text("login", true, "Login", null))
                    .accountField(AccountField.secret("password", true, "Password", null))
                    .accountField(AccountField.secret("code", false, "Authenticator secret", null))
                    .accountField(AccountField.flag("loud", "Tell the fans", null))
                    .build(),
            PlatformTraits.of(PLAIN, "Plain").build()));
    private static final BandId OTHER = new BandId(UUID.fromString("00000000-0000-0000-0000-000000000002"));

    private final AccountsFakes.Stores stores = AccountsFakes.Stores.withFirstBand();
    private final AccountsFakes.Accounts accounts = new AccountsFakes.Accounts();
    private final List<BandId> checked = new ArrayList<>();
    private final UserId owner = new UserId(UUID.randomUUID());
    private final UserId manager = new UserId(UUID.randomUUID());
    private final UserId stranger = new UserId(UUID.randomUUID());
    private final PlatformAccountService service;

    PlatformAccountServiceTest() {
        stores.save(new Band(OTHER, "other", "Other"));
        stores.save(new Membership(owner, BandId.FIRST, Role.OWNER, null));
        stores.save(new Membership(manager, BandId.FIRST, Role.MANAGER, null));
        stores.save(new Membership(stranger, OTHER, Role.OWNER, null));
        PlatformAccountCheck alphaCheck = new PlatformAccountCheck() {
            @Override
            public Platform platform() {
                return ALPHA;
            }

            @Override
            public AccountCheck.Outcome check(BandId band) {
                checked.add(band);
                return AccountCheck.Outcome.signedIn("Signed in as " + accounts.of(band, ALPHA).orElseThrow()
                        .setting("login"));
            }
        };
        service = new PlatformAccountService(accounts, PLATFORMS, AccountsFakes.service(new AccountsFakes.SignedIn(),
                stores, ""), new AesGcmSecretBox(AccountsFakes.TEST_KEYS), () -> List.of(alphaCheck),
                Clock.fixed(Instant.parse("2026-10-10T10:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void the_platforms_with_an_account_are_listed_unconnected_until_an_owner_connects_one() {
        assertThat(service.accounts(manager, BandId.FIRST)).singleElement()
                .satisfies(a -> {
                    assertThat(a.platform()).isEqualTo(ALPHA);
                    assertThat(a.connected()).isFalse();
                });

        AccountView connected = service.connect(owner, BandId.FIRST, ALPHA,
                Map.of("login", " band@x.test ", "loud", "yes"), Map.of("password", "hunter2"));

        assertThat(connected.connected()).isTrue();
        assertThat(connected.settings()).containsExactlyInAnyOrderEntriesOf(Map.of("login", "band@x.test",
                "loud", "false"));
        assertThat(connected.secretsSet()).containsExactly("password");
        assertThat(connected.toString()).doesNotContain("hunter2");
        assertThat(accounts.of(BandId.FIRST, ALPHA).orElseThrow().secret("password")).isEqualTo("hunter2");
    }

    @Test
    void only_owners_connect_and_disconnect_and_a_stranger_doesnt_find_the_band() {
        assertThatThrownBy(() -> service.connect(manager, BandId.FIRST, ALPHA, Map.of("login", "x"),
                Map.of("password", "y"))).isInstanceOfSatisfying(UserFacingException.class,
                e -> assertThat(e.code()).isEqualTo(AccountsErrorCode.OWNER_ONLY));
        assertThatThrownBy(() -> service.accounts(stranger, BandId.FIRST)).isInstanceOf(NotFoundException.class);
        service.connect(owner, BandId.FIRST, ALPHA, Map.of("login", "x"), Map.of("password", "y"));
        assertThatThrownBy(() -> service.disconnect(manager, BandId.FIRST, ALPHA))
                .isInstanceOf(UserFacingException.class);

        service.disconnect(owner, BandId.FIRST, ALPHA);

        assertThat(accounts.of(BandId.FIRST, ALPHA)).isEmpty();
    }

    @Test
    void what_the_platform_doesnt_have_or_needs_and_misses_is_refused() {
        assertThatThrownBy(() -> service.connect(owner, BandId.FIRST, ALPHA, Map.of("login", "x", "pin", "1",
                "password", "in the wrong place"), Map.of()))
                .isInstanceOfSatisfying(UserFacingException.class, e -> {
                    assertThat(e.code()).isEqualTo(AccountsErrorCode.INVALID_ACCOUNT);
                    assertThat(e.args()).containsEntry("fields", "password,pin");
                });
        assertThatThrownBy(() -> service.connect(owner, BandId.FIRST, PLAIN, Map.of(), Map.of()))
                .as("no account there").isInstanceOf(NotFoundException.class);
        assertThat(accounts.of(BandId.FIRST, ALPHA)).isEmpty();
    }

    @Test
    void a_secret_left_blank_is_kept_and_a_change_forgets_the_last_test() {
        service.connect(owner, BandId.FIRST, ALPHA, Map.of("login", "x"), Map.of("password", "old", "code", "c"));
        service.check(manager, BandId.FIRST, ALPHA);

        AccountView changed = service.connect(owner, BandId.FIRST, ALPHA, Map.of("login", "y"),
                Map.of("password", "", "code", "new"));

        assertThat(accounts.of(BandId.FIRST, ALPHA).orElseThrow().secrets())
                .containsExactlyInAnyOrderEntriesOf(Map.of("password", "old", "code", "new"));
        assertThat(changed.lastCheck()).isNull();
    }

    @Test
    void any_member_tests_the_sign_in_and_its_answer_is_kept() {
        assertThatThrownBy(() -> service.check(manager, BandId.FIRST, ALPHA)).as("not connected")
                .isInstanceOf(NotFoundException.class);
        service.connect(owner, BandId.FIRST, ALPHA, Map.of("login", "band@x.test"), Map.of("password", "p"));

        AccountView tested = service.check(manager, BandId.FIRST, ALPHA);

        assertThat(checked).containsExactly(BandId.FIRST);
        assertThat(tested.lastCheck()).isEqualTo(new AccountCheck(true, "Signed in as band@x.test",
                Instant.parse("2026-10-10T10:00:00Z")));
        assertThat(service.accounts(owner, BandId.FIRST).getFirst().lastCheck()).isEqualTo(tested.lastCheck());
    }

    @Test
    void a_bands_account_is_its_own() {
        service.connect(owner, BandId.FIRST, ALPHA, Map.of("login", "x"), Map.of("password", "y"));

        assertThat(service.accounts(stranger, OTHER)).singleElement()
                .satisfies(a -> assertThat(a.connected()).isFalse());
        assertThat(accounts.of(OTHER, ALPHA)).isEmpty();
    }

    @Test
    void the_configurations_account_is_kept_once_and_checked_like_any_other() {
        PlatformAccountDirectory directory = new PlatformAccountDirectory(accounts, PLATFORMS,
                new AesGcmSecretBox(AccountsFakes.TEST_KEYS));

        assertThat(directory.adopt(BandId.FIRST, ALPHA, Map.of("login", "x"), Map.of("password", "y"))).isTrue();
        assertThat(directory.adopt(BandId.FIRST, ALPHA, Map.of("login", "z"), Map.of("password", "w"))).isFalse();
        assertThat(directory.of(BandId.FIRST, ALPHA).orElseThrow().setting("login")).isEqualTo("x");
        assertThatThrownBy(() -> directory.adopt(OTHER, ALPHA, Map.of(), Map.of("password", "y")))
                .isInstanceOf(UserFacingException.class);
        assertThat(new PlatformAccountDirectory(accounts, PLATFORMS, new AesGcmSecretBox(""))
                .adopt(OTHER, ALPHA, Map.of("login", "x"), Map.of("password", "y"))).as("no key: not kept").isFalse();
    }
}
