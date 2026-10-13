package sk.drabikp.gigsynchub.calendar.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import sk.drabikp.gigsynchub.gig.application.port.out.AcrossBands;

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

    /**
     * Events long gone from a band's calendar with nothing left for anyone to look at: removed
     * before the date, and no unseen change held against them ({@code changeType} is what the page
     * shows and what marking it seen clears).
     */
    @Modifying
    @AcrossBands("keeping the table bounded is every band's housekeeping, and no band's own calendar")
    @Query("delete from CalendarEventEntity e where e.removedAt is not null and e.removedAt < :before "
            + "and e.changeType is null")
    int forgetRemovedBefore(Instant before);
}
