package sk.drabikp.gigsynchub.calendar.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Spring Data repository for {@link CalendarLinkEntity}; used only by {@link JpaCalendarLinkStore}. */
interface CalendarLinkJpaRepository extends JpaRepository<CalendarLinkEntity, BandEventKey> {

    List<CalendarLinkEntity> findByBandId(UUID bandId);

}
