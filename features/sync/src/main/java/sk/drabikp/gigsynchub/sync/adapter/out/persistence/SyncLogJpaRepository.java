package sk.drabikp.gigsynchub.sync.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Spring Data repository for {@link SyncLogEntity}; used only by {@link JpaSyncOutbox}. */
interface SyncLogJpaRepository extends JpaRepository<SyncLogEntity, Long> {

    /**
     * One task's history, for the band that asked. The log used to have no band of its own and this
     * read carried {@code @AcrossBands}, with the band checked a layer up by hand; V14 gave it the
     * column every sibling table has, so the band is part of the asking and this repository is
     * inside {@code BandScopedQueriesTest}'s enumeration like any other.
     */
    List<SyncLogEntity> findByBandIdAndTaskIdOrderByIdAsc(UUID bandId, Long taskId);
}
