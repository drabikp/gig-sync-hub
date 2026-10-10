package sk.drabikp.bzscraper.accounts.application;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.accounts.application.port.in.AccountsErrorCode;
import sk.drabikp.bzscraper.accounts.application.port.in.BandsUseCase.MyBand;
import sk.drabikp.bzscraper.accounts.domain.Membership;
import sk.drabikp.bzscraper.accounts.domain.Role;
import sk.drabikp.bzscraper.accounts.domain.User;
import sk.drabikp.bzscraper.gig.application.NotFoundException;
import sk.drabikp.bzscraper.gig.application.UserFacingException;
import sk.drabikp.bzscraper.gig.domain.BandId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountsServiceTest {

    private final AccountsFakes.SignedIn login = new AccountsFakes.SignedIn();
    private final AccountsFakes.Stores stores = AccountsFakes.Stores.withFirstBand();

    private User signIn(AccountsService service, String subject, String email) {
        login.as(subject, email);
        return service.current().orElseThrow();
    }

    @Test
    void the_first_sign_in_makes_the_user_once_and_nobody_is_there_without_one() {
        AccountsService service = AccountsFakes.service(login, stores, "");
        assertThat(service.current()).isEmpty();

        User first = signIn(service, "sub-1", "drummer@band.test");
        User again = signIn(service, "sub-1", "drummer@band.test");

        assertThat(again.id()).isEqualTo(first.id());
    }

    @Test
    void the_first_band_goes_to_its_named_owner_and_to_nobody_else() {
        AccountsService service = AccountsFakes.service(login, stores, "Owner@Band.test");

        User stranger = signIn(service, "sub-1", "stranger@else.test");
        User owner = signIn(service, "sub-2", "owner@band.test");

        assertThat(service.role(stranger.id(), BandId.FIRST)).isEmpty();
        assertThat(service.role(owner.id(), BandId.FIRST)).contains(Role.OWNER);
    }

    @Test
    void without_a_named_owner_the_first_to_sign_in_owns_the_first_band_and_only_they() {
        AccountsService service = AccountsFakes.service(login, stores, "");

        User first = signIn(service, "sub-1", "a@band.test");
        User second = signIn(service, "sub-2", "b@band.test");

        assertThat(service.role(first.id(), BandId.FIRST)).contains(Role.OWNER);
        assertThat(service.role(second.id(), BandId.FIRST)).isEmpty();
    }

    @Test
    void a_new_band_is_its_makers_and_nobody_elses_and_an_address_is_one_bands() {
        AccountsService service = AccountsFakes.service(login, stores, "nobody@x.test");
        User maker = signIn(service, "sub-1", "a@band.test");
        User other = signIn(service, "sub-2", "b@band.test");

        MyBand made = service.create(maker.id(), "The Band", "the-band");

        assertThat(made.role()).isEqualTo(Role.OWNER);
        assertThat(service.myBands(maker.id())).extracting(b -> b.band().slug()).containsExactly("the-band");
        assertThat(service.myBands(other.id())).isEmpty();
        assertThatThrownBy(() -> service.band(other.id(), made.band().id())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.create(other.id(), "Another", "the-band"))
                .isInstanceOfSatisfying(UserFacingException.class,
                        e -> assertThat(e.code()).isEqualTo(AccountsErrorCode.SLUG_TAKEN));
        assertThatThrownBy(() -> service.create(other.id(), " ", "Not A Slug"))
                .isInstanceOfSatisfying(UserFacingException.class, e -> {
                    assertThat(e.code()).isEqualTo(AccountsErrorCode.INVALID_BAND);
                    assertThat(e.args()).containsEntry("fields", "name,slug");
                });
    }

    @Test
    void only_an_owner_renames_a_band_and_members_see_each_other() {
        AccountsService service = AccountsFakes.service(login, stores, "nobody@x.test");
        User owner = signIn(service, "sub-1", "a@band.test");
        User manager = signIn(service, "sub-2", "b@band.test");
        MyBand band = service.create(owner.id(), "The Band", "the-band");
        stores.save(new Membership(manager.id(), band.band().id(), Role.MANAGER,
                "Bee"));

        assertThatThrownBy(() -> service.update(manager.id(), band.band().id(), "Renamed", "renamed", null))
                .isInstanceOfSatisfying(UserFacingException.class,
                        e -> assertThat(e.code()).isEqualTo(AccountsErrorCode.OWNER_ONLY));
        assertThat(service.update(owner.id(), band.band().id(), "Renamed", "renamed", true).band().slug()).isEqualTo("renamed");
        assertThat(AccountsFakes.memberships(stores, new AccountsFakes.Invitations(), java.time.Clock.systemUTC())
                .members(manager.id(), band.band().id())).extracting(m -> m.role())
                .containsExactly(Role.OWNER, Role.MANAGER);
    }

    @Test
    void the_first_band_is_named_from_the_configuration_once() {
        AccountsService service = AccountsFakes.service(login, stores, "");

        service.nameFirstBand("eufory", "Eufory");
        service.nameFirstBand("other", "Other");

        assertThat(stores.byId(BandId.FIRST)).get().satisfies(b -> {
            assertThat(b.slug()).isEqualTo("eufory");
            assertThat(b.name()).isEqualTo("Eufory");
        });
    }
}
