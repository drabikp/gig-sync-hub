package sk.drabikp.gigsynchub.accounts.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Spring Data repository for {@link InvitationEntity}; used only by {@link JpaAccountsStores}. */
interface InvitationJpaRepository extends JpaRepository<InvitationEntity, UUID> {

    Optional<InvitationEntity> findByTokenHash(String tokenHash);

    Optional<InvitationEntity> findByBandIdAndId(UUID bandId, UUID id);

    List<InvitationEntity> findByBandIdAndAcceptedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(UUID bandId,
                                                                                               Instant now);
}
