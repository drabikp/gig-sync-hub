package sk.drabikp.gigsynchub.accounts.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/** Spring Data repository for {@link BandEntity}; used only by {@link JpaAccountsStores}. */
interface BandJpaRepository extends JpaRepository<BandEntity, UUID> {

    Optional<BandEntity> findBySlug(String slug);

    /**
     * Takes the band's row until the transaction ends — the hold behind
     * {@link sk.drabikp.gigsynchub.accounts.application.port.out.MembershipStore#hold}. It asks for a
     * row lock, which is what it is, and it selects the id alone so that no entity comes into the
     * session: nothing here wants the band, only the right to decide about it alone.
     *
     * <p>The lock is asked for as a {@link LockModeType}, not written into the query, so the DIALECT
     * words it — {@code for update} on PostgreSQL, a table hint on SQL Server. Written out by hand it
     * would be one more thing tying this schema to one database, and this one needn't be.
     * {@code BandKeepsAnOwnerTest} is what says the hold still holds: it runs two real transactions
     * and fails exactly when the second one does not wait. That is not a hope — take the annotation
     * away and BOTH of its tests fail (checked 2026-10-12), so it is a detector and not decoration.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b.id from BandEntity b where b.id = :id")
    Optional<UUID> hold(@Param("id") UUID id);
}
