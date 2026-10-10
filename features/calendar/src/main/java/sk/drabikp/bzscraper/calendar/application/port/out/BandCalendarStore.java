package sk.drabikp.bzscraper.calendar.application.port.out;

import sk.drabikp.bzscraper.calendar.domain.BandCalendar;
import sk.drabikp.bzscraper.gig.domain.BandId;

import java.util.Optional;

/** Where each band's calendar is read from; its address kept encrypted. */
public interface BandCalendarStore {

    Optional<BandCalendar> of(BandId band);

    void save(BandCalendar calendar);

    void delete(BandId band);

    /** Seals again every address an older key sealed; how many. */
    int reseal();
}
