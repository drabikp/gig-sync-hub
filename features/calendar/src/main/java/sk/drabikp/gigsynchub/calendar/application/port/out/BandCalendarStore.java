package sk.drabikp.gigsynchub.calendar.application.port.out;

import org.jmolecules.ddd.annotation.Repository;
import sk.drabikp.gigsynchub.calendar.domain.BandCalendar;
import sk.drabikp.gigsynchub.gig.domain.BandId;

import java.util.Optional;

/** Where each band's calendar is read from; its address kept encrypted. */
@Repository
public interface BandCalendarStore {

    Optional<BandCalendar> of(BandId band);

    void save(BandCalendar calendar);

    void delete(BandId band);

    /** Seals again every address an older key sealed; how many. */
    int reseal();
}
