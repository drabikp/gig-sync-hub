package sk.drabikp.gigsynchub.sync.adapter.out.persistence;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import sk.drabikp.gigsynchub.gig.application.port.out.AcrossBands;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Spring Data repository for {@link SyncTaskEntity}; used only by {@link JpaSyncOutbox}. */
interface SyncTaskJpaRepository extends JpaRepository<SyncTaskEntity, Long> {

    List<SyncTaskEntity> findByBandIdAndGigIdAndStatusInOrderByIdAsc(UUID bandId, UUID gigId,
                                                                    Collection<String> statuses);

    List<SyncTaskEntity> findByBandIdAndStatusInOrderByIdAsc(UUID bandId, Collection<String> statuses);

    @AcrossBands("after a restart, every band's RUNNING work has to be picked up or failed")
    List<SyncTaskEntity> findByStatusInOrderByIdAsc(Collection<String> statuses);

    List<SyncTaskEntity> findByBandIdAndGigIdAndPlatformAndStatusOrderByIdAsc(UUID bandId, UUID gigId,
                                                                             String platform, String status);

    List<SyncTaskEntity> findByBandIdAndGigId(UUID bandId, UUID gigId);

    @AcrossBands("the worker asks whether ANY band has work due before it asks whose turn it is")
    Optional<SyncTaskEntity> findFirstByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(String status, Instant now);

    @AcrossBands("FairTurns puts the bands in order, so it has to see every band's due work at once")
    List<SyncTaskEntity> findByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(String status, Instant now);

    List<SyncTaskEntity> findByBandIdAndPlatformAndActionAndStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
            UUID bandId, String platform, String action, String status, Instant now);

    /** The activity log: what happened last, first — and the latest `limit` of those, not of the oldest. */
    List<SyncTaskEntity> findByBandIdOrderByUpdatedAtDescIdDesc(UUID bandId, Limit limit);

    /** A gig's own activity, latest first. */
    List<SyncTaskEntity> findByBandIdAndGigIdOrderByUpdatedAtDescIdDesc(UUID bandId, UUID gigId, Limit limit);
}
