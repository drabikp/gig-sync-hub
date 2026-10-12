package sk.drabikp.gigsynchub.sync.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import sk.drabikp.gigsynchub.gig.application.port.out.AcrossBands;

import java.util.List;

/** Spring Data repository for {@link SyncLogEntity}; used only by {@link JpaSyncOutbox}. */
interface SyncLogJpaRepository extends JpaRepository<SyncLogEntity, Long> {

    /**
     * A task's own history. The log carries no band of its own — it goes with its task — so the band
     * is checked where the task is found: {@code SyncLogService#log} asks for the task by id and
     * refuses it (404) unless it is this band's, and only then reads the lines.
     */
    @AcrossBands("the log has no band column; its task's band is checked in SyncLogService#log")
    List<SyncLogEntity> findByTaskIdOrderByIdAsc(Long taskId);
}
