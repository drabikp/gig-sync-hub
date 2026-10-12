package sk.drabikp.gigsynchub.calendar.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

/** Spring Data repository for {@link CalendarRuleEntity}; used only by {@link JpaBandProfileStore}. */
interface CalendarRuleJpaRepository extends JpaRepository<CalendarRuleEntity, Long> {

    /** The shipped rules (no band) and the band's own, in the order they were stored. */
    @Query("select r from CalendarRuleEntity r where r.bandId is null or r.bandId = :bandId order by r.id")
    List<CalendarRuleEntity> forBand(UUID bandId);

    @Modifying
    @Query("delete from CalendarRuleEntity r where r.bandId is null and r.origin = :origin")
    void deleteShipped(String origin);

    @Modifying
    @Query("delete from CalendarRuleEntity r where r.bandId = :bandId and r.origin = :origin")
    void deleteOwn(UUID bandId, String origin);
}
