package sk.drabikp.gigsynchub.accounts.adapter.out.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import sk.drabikp.gigsynchub.gig.application.port.out.AcrossBands;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Spring Data repository for {@link AuditEventEntity}; used only by {@link JpaAccountsStores}. */
interface AuditEventJpaRepository extends JpaRepository<AuditEventEntity, Long> {

    @Modifying
    @AcrossBands("keeping the table bounded is every band's housekeeping, and no band's own history")
    @Query("delete from AuditEventEntity e where e.at < :before")
    int forgetBefore(Instant before);

    List<AuditEventEntity> findByBandIdOrderByAtDescIdDesc(UUID bandId, Pageable page);
}
