package sk.drabikp.bzscraper.accounts.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Spring Data repository for {@link BandEntity}; used only by {@link JpaAccountsStores}. */
interface BandJpaRepository extends JpaRepository<BandEntity, UUID> {

    Optional<BandEntity> findBySlug(String slug);
}
