package sk.drabikp.bzscraper.accounts.adapter.out.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import sk.drabikp.bzscraper.accounts.domain.Band;
import sk.drabikp.bzscraper.accounts.domain.Membership;
import sk.drabikp.bzscraper.accounts.domain.Role;
import sk.drabikp.bzscraper.accounts.domain.User;
import sk.drabikp.bzscraper.accounts.domain.UserId;
import sk.drabikp.bzscraper.gig.domain.BandId;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class JpaAccountsStoresTest {

    @Autowired
    private JpaAccountsStores stores;

    @Test
    void the_first_band_is_there_after_the_migrations() {
        assertThat(stores.byId(BandId.FIRST)).isPresent();
    }

    @Test
    void users_bands_and_memberships_round_trip() {
        User user = new User(new UserId(UUID.randomUUID()), "sub-1", "a@band.test", "Ann");
        Band band = new Band(new BandId(UUID.randomUUID()), "the-band", "The Band");
        stores.save(user);
        stores.save(band);
        stores.save(new Membership(user.id(), band.id(), Role.OWNER, "Ann B."));

        assertThat(stores.bySubject("sub-1")).contains(user);
        assertThat(stores.bySlug("the-band")).contains(band);
        assertThat(stores.ofUser(user.id())).singleElement().satisfies(m -> {
            assertThat(m.band()).isEqualTo(band.id());
            assertThat(m.role()).isEqualTo(Role.OWNER);
            assertThat(m.displayName()).isEqualTo("Ann B.");
        });
        assertThat(stores.ofBand(band.id())).hasSize(1);
        assertThat(stores.of(user.id(), BandId.FIRST)).isEmpty();
    }
}
