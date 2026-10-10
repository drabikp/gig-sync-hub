package sk.drabikp.bzscraper.calendar.adapter.out.persistence;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sk.drabikp.bzscraper.calendar.application.port.out.CalendarSnapshotStore;
import sk.drabikp.bzscraper.calendar.domain.KnownCalendarEvent;
import sk.drabikp.bzscraper.gig.domain.BandId;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** JPA-backed {@link CalendarSnapshotStore}: table {@code calendar_event}. */
@Component
@Transactional(readOnly = true)
class JpaCalendarSnapshotStore implements CalendarSnapshotStore {

    private final CalendarEventJpaRepository jpaRepository;
    private final CalendarEntityMapper mapper;

    public JpaCalendarSnapshotStore(CalendarEventJpaRepository jpaRepository, CalendarEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Map<String, KnownCalendarEvent> all(BandId band) {
        return jpaRepository.findByBandId(band.value()).stream().map(mapper::toDomain)
                .collect(Collectors.toMap(KnownCalendarEvent::id, Function.identity()));
    }

    @Override
    public Optional<KnownCalendarEvent> find(BandId band, String eventId) {
        return jpaRepository.findById(new BandEventKey(band.value(), eventId)).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public void saveRead(BandId band, Collection<KnownCalendarEvent> events, Instant readAt) {
        Map<String, CalendarEventEntity> stored = jpaRepository.findByBandIdAndEventUidIn(band.value(),
                        events.stream().map(KnownCalendarEvent::id).toList()).stream()
                .collect(Collectors.toMap(CalendarEventEntity::getEventUid, Function.identity()));
        List<CalendarEventEntity> changed = events.stream().map(known -> {
            CalendarEventEntity entity = stored.getOrDefault(known.id(),
                    new CalendarEventEntity(band.value(), known.id()));
            mapper.update(known, entity);
            entity.seen(known.removed() ? null : readAt, known.firstSeen());
            return entity;
        }).toList();
        jpaRepository.saveAll(changed);
    }

    @Override
    @Transactional
    public void save(BandId band, KnownCalendarEvent event) {
        CalendarEventEntity entity = jpaRepository.findById(new BandEventKey(band.value(), event.id()))
                .orElseGet(() -> new CalendarEventEntity(band.value(), event.id()));
        mapper.update(event, entity);
        entity.seen(null, event.firstSeen());
        jpaRepository.save(entity);
    }

    @Override
    @Transactional
    public void remove(BandId band, String eventId) {
        jpaRepository.deleteById(new BandEventKey(band.value(), eventId));
    }

    @Override
    public Optional<Instant> lastRead(BandId band) {
        return Optional.ofNullable(jpaRepository.lastSeen(band.value()));
    }
}
