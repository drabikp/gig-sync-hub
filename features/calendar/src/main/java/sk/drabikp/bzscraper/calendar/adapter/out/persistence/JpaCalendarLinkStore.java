package sk.drabikp.bzscraper.calendar.adapter.out.persistence;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sk.drabikp.bzscraper.calendar.application.port.out.CalendarLinkStore;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.GigId;

import java.time.Clock;
import java.util.Map;
import java.util.stream.Collectors;

/** JPA-backed {@link CalendarLinkStore}: table {@code calendar_link}. */
@Component
@Transactional(readOnly = true)
class JpaCalendarLinkStore implements CalendarLinkStore {

    private final CalendarLinkJpaRepository jpaRepository;
    private final Clock clock;

    public JpaCalendarLinkStore(CalendarLinkJpaRepository jpaRepository, Clock clock) {
        this.jpaRepository = jpaRepository;
        this.clock = clock;
    }

    @Override
    public Map<String, GigId> all(BandId band) {
        return jpaRepository.findByBandId(band.value()).stream().collect(Collectors.toMap(
                CalendarLinkEntity::getEventUid, l -> new GigId(l.getGigId())));
    }

    @Override
    @Transactional
    public void link(BandId band, String eventId, GigId gigId) {
        jpaRepository.save(new CalendarLinkEntity(band.value(), eventId, gigId.value(), clock.instant()));
    }

    @Override
    @Transactional
    public void unlink(BandId band, String eventId) {
        jpaRepository.deleteById(new BandEventKey(band.value(), eventId));
    }

}
