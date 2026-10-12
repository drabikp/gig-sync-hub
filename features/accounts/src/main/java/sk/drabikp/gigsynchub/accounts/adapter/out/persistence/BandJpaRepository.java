package sk.drabikp.gigsynchub.accounts.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/** Spring Data repository for {@link BandEntity}; used only by {@link JpaAccountsStores}. */
interface BandJpaRepository extends JpaRepository<BandEntity, UUID> {

    Optional<BandEntity> findBySlug(String slug);

    /**
     * Takes the band's row until the transaction ends — the hold behind
     * {@link sk.drabikp.gigsynchub.accounts.application.port.out.MembershipStore#hold}. Asked of the
     * database rather than of JPA because that is what it is (a row lock), and it takes no entity
     * into the session: nothing here wants the band, only the right to decide about it alone.
     */
    @Query(value = "select id from band where id = :id for update", nativeQuery = true)
    Optional<UUID> hold(@Param("id") UUID id);
}
