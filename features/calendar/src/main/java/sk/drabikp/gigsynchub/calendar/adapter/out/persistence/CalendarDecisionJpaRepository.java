package sk.drabikp.gigsynchub.calendar.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Spring Data repository for {@link CalendarDecisionEntity}; used only by {@link JpaCalendarDecisionStore}. */
interface CalendarDecisionJpaRepository extends JpaRepository<CalendarDecisionEntity, BandEventKey> {

    List<CalendarDecisionEntity> findByBandId(UUID bandId);
}
