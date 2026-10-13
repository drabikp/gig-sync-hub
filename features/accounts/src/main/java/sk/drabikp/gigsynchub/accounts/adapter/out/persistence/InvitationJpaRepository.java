package sk.drabikp.gigsynchub.accounts.adapter.out.persistence;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import sk.drabikp.gigsynchub.gig.application.port.out.AcrossBands;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Spring Data repository for {@link InvitationEntity}; used only by {@link JpaAccountsStores}. */
interface InvitationJpaRepository extends JpaRepository<InvitationEntity, UUID> {

    @Modifying
    @AcrossBands("keeping the table bounded is every band's housekeeping, and no band's own invitations")
    @Query("delete from InvitationEntity i where i.expiresAt < :before")
    int forgetExpiredBefore(Instant before);

    Optional<InvitationEntity> findByTokenHash(String tokenHash);

    Optional<InvitationEntity> findByBandIdAndId(UUID bandId, UUID id);

    List<InvitationEntity> findByBandIdAndAcceptedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(UUID bandId,
                                                                                               Instant now);
}
