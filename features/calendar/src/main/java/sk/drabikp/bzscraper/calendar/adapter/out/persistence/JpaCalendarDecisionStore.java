package sk.drabikp.bzscraper.calendar.adapter.out.persistence;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sk.drabikp.bzscraper.calendar.application.port.out.CalendarDecisionStore;
import sk.drabikp.bzscraper.calendar.domain.event.CalendarEventKind;
import sk.drabikp.bzscraper.gig.domain.BandId;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

/** JPA-backed {@link CalendarDecisionStore}: table {@code calendar_decision}. */
@Component
@Transactional(readOnly = true)
class JpaCalendarDecisionStore implements CalendarDecisionStore {

    private final CalendarDecisionJpaRepository jpaRepository;
    private final Clock clock;

    public JpaCalendarDecisionStore(CalendarDecisionJpaRepository jpaRepository, Clock clock) {
        this.jpaRepository = jpaRepository;
        this.clock = clock;
    }

    @Override
    public Map<String, CalendarEventKind> all(BandId band) {
        return jpaRepository.findByBandId(band.value()).stream().collect(Collectors.toMap(
                CalendarDecisionEntity::getEventUid, d -> CalendarEventKind.valueOf(d.getVerdict())));
    }

    @Override
    @Transactional
    public void decide(BandId band, String eventId, CalendarEventKind verdict) {
        jpaRepository.save(new CalendarDecisionEntity(band.value(), eventId, verdict.name(), LocalDateTime.now(clock)));
    }

    @Override
    @Transactional
    public void forget(BandId band, String eventId) {
        jpaRepository.deleteById(new BandEventKey(band.value(), eventId));
    }
}
