package sk.drabikp.gigsynchub.calendar.domain;

import org.jmolecules.ddd.annotation.Service;
import sk.drabikp.gigsynchub.calendar.domain.CatalogMatch.Difference;
import sk.drabikp.gigsynchub.calendar.domain.CatalogMatch.Kind;
import sk.drabikp.gigsynchub.calendar.domain.CatalogMatch.State;
import sk.drabikp.gigsynchub.calendar.domain.event.CalendarEventStatus;
import sk.drabikp.gigsynchub.gig.domain.Gig;
import sk.drabikp.gigsynchub.gig.domain.GigId;
import sk.drabikp.gigsynchub.gig.domain.GigSchedule;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pure domain service: where a calendar event stands against the catalog
 * ({@link CatalogMatch}). A linked gig is compared with what the calendar says now — only
 * for gigs that are still to come, as past gigs aren't changed any more: another day,
 * another show time, cancelled in the calendar, or the event gone from the calendar. The
 * calendar's show is the BAND's: it is compared with the gig's slot when it has one; a
 * multi-day event without a slot matches on any of its days and is offered the calendar's
 * time as its slot. An unlinked event is matched to the catalog's gigs on the show's day
 * (any day of a multi-day event), for the user to link.
 */
@Service
public final class CalendarCatalogMatcher {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH);

    private CalendarCatalogMatcher() {
    }

    public static CatalogMatch match(CalendarGigDraft draft, CalendarEventStatus status, boolean removed,
                                     GigId link, Map<GigId, Gig> catalog, LocalDate today) {
        Gig linked = link == null ? null : catalog.get(link);
        if (linked != null) {
            return new CatalogMatch(State.LINKED, linked, List.of(), differences(draft, status, removed, linked, today));
        }
        List<Gig> sameDay = sameDay(draft.date(), catalog.values());
        if (!sameDay.isEmpty()) {
            return new CatalogMatch(State.SAME_DAY, sameDay.size() == 1 ? sameDay.getFirst() : null, sameDay, List.of());
        }
        return new CatalogMatch(State.MISSING, null, List.of(), List.of());
    }

    private static List<Difference> differences(CalendarGigDraft draft, CalendarEventStatus status, boolean removed,
                                                Gig gig, LocalDate today) {
        GigSchedule schedule = gig.schedule();
        LocalDate showDay = schedule.showStart().toLocalDate();
        if (showDay.isBefore(today) && draft.date().isBefore(today)) {
            return List.of();
        }
        List<Difference> differences = new ArrayList<>();
        if (removed) {
            differences.add(new Difference(Kind.REMOVED, "gone from the calendar"));
        }
        if (status == CalendarEventStatus.CANCELLED && !gig.cancelled()) {
            differences.add(new Difference(Kind.CANCELLED, "cancelled in the calendar"));
        }
        if (removed) {
            return differences;
        }
        boolean dayMoved = schedule.hasSlot() ? !draft.date().equals(showDay)
                : schedule.multiDay() ? !schedule.covers(draft.date()) : !draft.date().equals(schedule.startDate());
        if (dayMoved) {
            differences.add(new Difference(Kind.DATE, "calendar: " + DAY.format(draft.date()) + ", catalog: "
                    + DAY.format(showDay) + (schedule.hasSlot() ? " (the band's slot)" : ""),
                    draft.date().toString(), showDay.toString()));
        }
        if (draft.showTime() != null) {
            if (!schedule.hasSlot() && schedule.multiDay()) {
                differences.add(new Difference(Kind.SHOW_TIME, "the calendar gives the band's slot: "
                        + DAY.format(draft.date()) + " " + draft.showTime() + "; the catalog has only the whole event",
                        draft.date() + "T" + draft.showTime(), null));
            } else {
                LocalTime gigTime = schedule.showStart().toLocalTime();
                if (!draft.showTime().equals(gigTime)) {
                    differences.add(new Difference(Kind.SHOW_TIME,
                            "show time in the calendar: " + draft.showTime() + ", catalog: " + gigTime,
                            draft.showTime().toString(), gigTime.toString()));
                }
            }
        }
        return differences;
    }

    private static List<Gig> sameDay(LocalDate day, Collection<Gig> catalog) {
        return catalog.stream().filter(g -> g.schedule().covers(day)
                || g.schedule().showStart().toLocalDate().equals(day)).toList();
    }
}
