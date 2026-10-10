package sk.drabikp.bzscraper.accounts.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Spring Data repository for {@link AppUserEntity}; used only by {@link JpaAccountsStores}. */
interface AppUserJpaRepository extends JpaRepository<AppUserEntity, UUID> {

    Optional<AppUserEntity> findByRauthySub(String rauthySub);
}
