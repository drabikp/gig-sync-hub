package sk.drabikp.gigsynchub.accounts.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Spring Data repository for {@link MembershipEntity}; used only by {@link JpaAccountsStores}. */
interface MembershipJpaRepository extends JpaRepository<MembershipEntity, MembershipEntity.Key> {

    List<MembershipEntity> findByUserId(UUID userId);

    List<MembershipEntity> findByBandId(UUID bandId);
}
