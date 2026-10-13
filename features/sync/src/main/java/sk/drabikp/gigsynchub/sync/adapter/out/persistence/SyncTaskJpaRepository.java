package sk.drabikp.gigsynchub.sync.adapter.out.persistence;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
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

    /**
     * Which bands have work due — the band column alone, one row each. This is what the worker asks
     * before it asks anything else, so that deciding whose turn it is costs one small read rather
     * than every band's queue (see {@code FairTurns}).
     */
    @AcrossBands("whose turn it is is a question about all the bands at once; it reads no band's work")
    @Query("select distinct t.bandId from SyncTaskEntity t where t.status = :status and t.nextAttemptAt <= :now")
    List<UUID> bandsWithWorkDue(String status, Instant now);

    /**
     * The open work counted by the database: one row per band, status and "has it run before",
     * which is at most a handful of rows however many tasks there are. The Activity page's heading
     * and the operator's gauges read this instead of every open row.
     */
    @Query("""
            select t.bandId, t.status, case when t.attempts > 0 then true else false end, count(t)
            from SyncTaskEntity t
            where t.bandId in :bandIds and t.status in :statuses
            group by t.bandId, t.status, case when t.attempts > 0 then true else false end
            """)
    List<Object[]> countOpenWork(Collection<UUID> bandIds, Collection<String> statuses);

    /** One band's due work, oldest first. */
    List<SyncTaskEntity> findByBandIdAndStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
            UUID bandId, String status, Instant now, Limit limit);

    List<SyncTaskEntity> findByBandIdAndPlatformAndActionAndStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
            UUID bandId, String platform, String action, String status, Instant now);

    /** The activity log: what happened last, first — and the latest `limit` of those, not of the oldest. */
    List<SyncTaskEntity> findByBandIdOrderByUpdatedAtDescIdDesc(UUID bandId, Limit limit);

    /**
     * Work that is over and old. Its log goes with it: {@code sync_log.task_id} cascades on delete
     * (V2), so the lines are gone with the row rather than left pointing at nothing.
     */
    @Modifying
    @AcrossBands("keeping the tables bounded is every band's housekeeping, and no band's own work")
    @Query("delete from SyncTaskEntity t where t.status in :settled and t.updatedAt < :before")
    int forgetSettledBefore(Collection<String> settled, Instant before);

    /** A gig's own activity, latest first. */
    List<SyncTaskEntity> findByBandIdAndGigIdOrderByUpdatedAtDescIdDesc(UUID bandId, UUID gigId, Limit limit);
}
