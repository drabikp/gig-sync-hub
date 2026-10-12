package sk.drabikp.gigsynchub.accounts.adapter.out.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Spring Data repository for {@link AuditEventEntity}; used only by {@link JpaAccountsStores}. */
interface AuditEventJpaRepository extends JpaRepository<AuditEventEntity, Long> {

    List<AuditEventEntity> findByBandIdOrderByAtDescIdDesc(UUID bandId, Pageable page);
}
