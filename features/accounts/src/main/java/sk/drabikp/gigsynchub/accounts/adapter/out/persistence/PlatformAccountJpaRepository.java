package sk.drabikp.gigsynchub.accounts.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import sk.drabikp.gigsynchub.gig.application.port.out.AcrossBands;

import java.util.List;
import java.util.UUID;

/** Spring Data repository for {@link PlatformAccountEntity}; used only by {@link JpaPlatformAccountStore}. */
interface PlatformAccountJpaRepository extends JpaRepository<PlatformAccountEntity, PlatformAccountEntity.Key> {

    List<PlatformAccountEntity> findByBandIdOrderByPlatform(UUID bandId);

    /** The accounts that keep secrets (to seal again with a new key). */
    @AcrossBands("key rotation on start seals EVERY band's secrets again, so it must see them all")
    List<PlatformAccountEntity> findBySecretsIsNotNull();
}
