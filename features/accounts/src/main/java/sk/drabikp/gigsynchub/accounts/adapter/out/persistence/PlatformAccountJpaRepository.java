package sk.drabikp.gigsynchub.accounts.adapter.out.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import sk.drabikp.gigsynchub.gig.application.port.out.AcrossBands;

import java.util.List;
import java.util.UUID;

/** Spring Data repository for {@link PlatformAccountEntity}; used only by {@link JpaPlatformAccountStore}. */
interface PlatformAccountJpaRepository extends JpaRepository<PlatformAccountEntity, PlatformAccountEntity.Key> {

    List<PlatformAccountEntity> findByBandIdOrderByPlatform(UUID bandId);

    /**
     * The accounts that keep secrets, a page at a time (to seal them again with a new key). Paged
     * because this is every band's row in one list: on a server with a few thousand bands, reading
     * them all into one transaction to rotate a key is the biggest thing the app ever holds, and it
     * happens while it is starting up.
     */
    @AcrossBands("key rotation on start seals EVERY band's secrets again, so it must see them all")
    List<PlatformAccountEntity> findBySecretsIsNotNullOrderByBandIdAscPlatformAsc(Pageable page);
}
