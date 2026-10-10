package sk.drabikp.bzscraper.calendar.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Spring Data repository for {@link CalendarEventEntity}; used only by {@link JpaCalendarSnapshotStore}. */
interface CalendarEventJpaRepository extends JpaRepository<CalendarEventEntity, BandEventKey> {

    List<CalendarEventEntity> findByBandId(UUID bandId);

    List<CalendarEventEntity> findByBandIdAndEventUidIn(UUID bandId, Collection<String> eventUids);

    @Query("select max(e.lastSeen) from CalendarEventEntity e where e.bandId = :bandId")
    Instant lastSeen(UUID bandId);
}
