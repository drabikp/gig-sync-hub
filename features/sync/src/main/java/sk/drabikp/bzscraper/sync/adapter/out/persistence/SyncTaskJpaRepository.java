package sk.drabikp.bzscraper.sync.adapter.out.persistence;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Spring Data repository for {@link SyncTaskEntity}; used only by {@link JpaSyncOutbox}. */
interface SyncTaskJpaRepository extends JpaRepository<SyncTaskEntity, Long> {

    List<SyncTaskEntity> findByBandIdAndGigIdAndStatusInOrderByIdAsc(UUID bandId, String gigId,
                                                                    Collection<String> statuses);

    List<SyncTaskEntity> findByBandIdAndStatusInOrderByIdAsc(UUID bandId, Collection<String> statuses);

    List<SyncTaskEntity> findByStatusInOrderByIdAsc(Collection<String> statuses);

    List<SyncTaskEntity> findByBandIdAndGigIdAndPlatformAndStatusOrderByIdAsc(UUID bandId, String gigId,
                                                                             String platform, String status);

    List<SyncTaskEntity> findByBandIdAndGigId(UUID bandId, String gigId);

    Optional<SyncTaskEntity> findFirstByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(String status, Instant now);

    List<SyncTaskEntity> findByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(String status, Instant now);

    List<SyncTaskEntity> findByBandIdAndPlatformAndActionAndStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
            UUID bandId, String platform, String action, String status, Instant now);

    List<SyncTaskEntity> findByBandIdOrderByIdDesc(UUID bandId, Limit limit);

    List<SyncTaskEntity> findByBandIdAndGigIdOrderByIdDesc(UUID bandId, String gigId, Limit limit);
}
