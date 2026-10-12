package sk.drabikp.gigsynchub.calendar.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import sk.drabikp.gigsynchub.gig.application.port.out.AcrossBands;

import java.util.List;
import java.util.UUID;

/** Spring Data repository for {@link BandCalendarEntity}; used only by {@link JpaBandCalendarStore}. */
interface BandCalendarJpaRepository extends JpaRepository<BandCalendarEntity, UUID> {

    /** Every band's calendar — only to seal the addresses again with a new key. */
    @AcrossBands("key rotation on start seals EVERY band's calendar address again")
    @Query("select c from BandCalendarEntity c order by c.bandId")
    List<BandCalendarEntity> everyBandsCalendar();
}
