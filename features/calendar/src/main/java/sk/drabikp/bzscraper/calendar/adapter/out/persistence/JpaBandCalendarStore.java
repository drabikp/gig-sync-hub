package sk.drabikp.bzscraper.calendar.adapter.out.persistence;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sk.drabikp.bzscraper.accounts.application.port.in.SecretBox;
import sk.drabikp.bzscraper.calendar.application.port.out.BandCalendarStore;
import sk.drabikp.bzscraper.calendar.domain.BandCalendar;
import sk.drabikp.bzscraper.gig.domain.BandId;

import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * {@link BandCalendarStore} on JPA (table {@code band_calendar}): the address sealed by the accounts'
 * {@link SecretBox} for its band ({@code band-calendar:<band>}). Mapped by hand, not by MapStruct: a row is
 * sealed and opened, not copied.
 */
@Component
@Transactional
class JpaBandCalendarStore implements BandCalendarStore {

    private final BandCalendarJpaRepository calendars;
    private final SecretBox box;
    private final Clock clock;

    JpaBandCalendarStore(BandCalendarJpaRepository calendars, SecretBox box, Clock clock) {
        this.calendars = calendars;
        this.box = box;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BandCalendar> of(BandId band) {
        return calendars.findById(band.value()).map(this::toCalendar);
    }

    @Override
    public void save(BandCalendar calendar) {
        SecretBox.Sealed sealed = box.seal(calendar.address(), context(calendar.band()));
        BandCalendarEntity entity = new BandCalendarEntity();
        entity.setBandId(calendar.band().value());
        entity.setIcalUrl(sealed.data());
        entity.setKeyId(sealed.keyId());
        entity.setZone(calendar.zone().getId());
        entity.setUpdatedAt(clock.instant());
        calendars.save(entity);
    }

    @Override
    public void delete(BandId band) {
        calendars.deleteById(band.value());
    }

    @Override
    public int reseal() {
        if (!box.ready()) {
            return 0;
        }
        List<BandCalendarEntity> old = calendars.everyBandsCalendar().stream()
                .filter(entity -> !box.current(new SecretBox.Sealed(entity.getKeyId(), entity.getIcalUrl())))
                .toList();
        old.forEach(entity -> save(toCalendar(entity)));
        return old.size();
    }

    private BandCalendar toCalendar(BandCalendarEntity entity) {
        BandId band = new BandId(entity.getBandId());
        return new BandCalendar(band, box.open(new SecretBox.Sealed(entity.getKeyId(), entity.getIcalUrl()),
                context(band)), ZoneId.of(entity.getZone()));
    }

    private static String context(BandId band) {
        return "band-calendar:" + band;
    }
}
