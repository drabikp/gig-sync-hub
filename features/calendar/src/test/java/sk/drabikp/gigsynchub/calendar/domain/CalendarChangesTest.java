package sk.drabikp.gigsynchub.calendar.domain;

import org.junit.jupiter.api.Test;
import sk.drabikp.gigsynchub.calendar.domain.CalendarChange.Field;
import sk.drabikp.gigsynchub.calendar.domain.CalendarChange.Type;
import sk.drabikp.gigsynchub.calendar.domain.event.CalendarEvent;
import sk.drabikp.gigsynchub.calendar.domain.event.CalendarEventKind;
import sk.drabikp.gigsynchub.calendar.domain.event.CalendarEventStatus;
import sk.drabikp.gigsynchub.calendar.domain.rules.CalendarClassification;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarChangesTest {

    private static final Instant FIRST = Instant.parse("2026-10-01T10:00:00Z");
    private static final Instant SECOND = Instant.parse("2026-10-02T10:00:00Z");
    private static final Instant THIRD = Instant.parse("2026-10-03T10:00:00Z");

    private static CalendarEvent event(String id, String title, int hour) {
        return new CalendarEvent(id, title, "Klub 007, Praha", "", LocalDateTime.of(2026, 11, 20, hour, 0), null, false,
                false, false, CalendarEventStatus.CONFIRMED, null);
    }

    private static CalendarClassification read(CalendarEvent event, CalendarEventKind kind) {
        return new CalendarClassification(event, kind, kind, false, CalendarEventStatus.CONFIRMED, 0, List.of());
    }

    private static Map<String, KnownCalendarEvent> saved(List<KnownCalendarEvent> copy) {
        return copy.stream().collect(Collectors.toMap(KnownCalendarEvent::id, Function.identity()));
    }

    private static CalendarChange changeOf(List<KnownCalendarEvent> copy, String id) {
        return saved(copy).get(id).change();
    }

    @Test
    void the_first_read_is_the_baseline_and_an_event_seen_later_is_new() {
        List<KnownCalendarEvent> first = CalendarChanges.afterRead(Map.of(),
                List.of(read(event("a", "Fest", 20), CalendarEventKind.GIG)), FIRST);
        assertThat(changeOf(first, "a")).isNull();

        List<KnownCalendarEvent> second = CalendarChanges.afterRead(saved(first), List.of(
                read(event("a", "Fest", 20), CalendarEventKind.GIG), read(event("b", "Rehearsal", 18),
                        CalendarEventKind.NOT_GIG)), SECOND);
        assertThat(changeOf(second, "a")).isNull();
        assertThat(changeOf(second, "b").type()).isEqualTo(Type.NEW);
    }

    @Test
    void changes_add_up_until_seen_keeping_what_the_rules_said_first() {
        List<KnownCalendarEvent> first = CalendarChanges.afterRead(Map.of(),
                List.of(read(event("a", "Fest?", 20), CalendarEventKind.UNSURE)), FIRST);
        List<KnownCalendarEvent> second = CalendarChanges.afterRead(saved(first),
                List.of(read(event("a", "Fest", 20), CalendarEventKind.GIG)), SECOND);
        List<KnownCalendarEvent> third = CalendarChanges.afterRead(saved(second),
                List.of(read(event("a", "Fest", 21), CalendarEventKind.GIG)), THIRD);

        CalendarChange change = changeOf(third, "a");
        assertThat(change.type()).isEqualTo(Type.CHANGED);
        assertThat(change.fields()).containsExactlyInAnyOrder(Field.TITLE, Field.TIME);
        assertThat(change.suggestedBefore()).as("what the rules said before the first change")
                .isEqualTo(CalendarEventKind.UNSURE);
        assertThat(change.at()).isEqualTo(THIRD);
    }

    @Test
    void an_event_gone_from_the_calendar_is_kept_as_removed_and_one_that_comes_back_has_returned() {
        List<KnownCalendarEvent> first = CalendarChanges.afterRead(Map.of(),
                List.of(read(event("a", "Fest", 20), CalendarEventKind.GIG)), FIRST);

        List<KnownCalendarEvent> gone = CalendarChanges.afterRead(saved(first), List.of(), SECOND);
        assertThat(saved(gone).get("a").removed()).isTrue();
        assertThat(changeOf(gone, "a").type()).isEqualTo(Type.REMOVED);
        assertThat(CalendarChanges.afterRead(saved(gone), List.of(), THIRD))
                .as("removed once: the saved copy keeps it as it is, nothing new to save").isEmpty();

        List<KnownCalendarEvent> back = CalendarChanges.afterRead(saved(gone),
                List.of(read(event("a", "Fest", 20), CalendarEventKind.GIG)), THIRD);
        assertThat(saved(back).get("a").removed()).isFalse();
        assertThat(changeOf(back, "a").type()).isEqualTo(Type.RETURNED);
        assertThat(saved(back).get("a").firstSeen()).isEqualTo(FIRST);
    }

    @Test
    void the_compared_fields_are_title_time_place_notes_and_status() {
        CalendarEvent before = event("a", "Fest", 20);
        CalendarEvent after = new CalendarEvent("a", "Fest", "Lucerna, Praha", "fee 300", before.start(), null, false,
                false, false, CalendarEventStatus.CANCELLED, null);

        assertThat(CalendarChanges.compare(before, after))
                .containsExactlyInAnyOrder(Field.PLACE, Field.NOTES, Field.STATUS);
        assertThat(CalendarChanges.compare(before, before)).isEmpty();
    }
}
