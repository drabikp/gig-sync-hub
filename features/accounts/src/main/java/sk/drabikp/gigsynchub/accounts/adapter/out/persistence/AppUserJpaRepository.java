package sk.drabikp.gigsynchub.accounts.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/** Spring Data repository for {@link AppUserEntity}; used only by {@link JpaAccountsStores}. */
interface AppUserJpaRepository extends JpaRepository<AppUserEntity, UUID> {

    Optional<AppUserEntity> findByRauthySub(String rauthySub);

    /**
     * Takes the user's row until the transaction ends — the hold behind
     * {@link sk.drabikp.gigsynchub.accounts.application.port.out.UserStore#hold}. The id alone is
     * selected, so no entity comes into the session: nothing here wants the user, only the right to
     * decide about what they own alone. The lock is asked for as a {@link LockModeType} rather than
     * written into the query, so the dialect words it — see {@link BandJpaRepository#hold}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u.id from AppUserEntity u where u.id = :id")
    Optional<UUID> hold(@Param("id") UUID id);
}
