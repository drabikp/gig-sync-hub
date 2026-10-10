package sk.drabikp.bzscraper.calendar.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The calendar as last read ({@code lastRead}, null if never), as the page shows it on a day:
 * its events latest first, the summary's counts, the events link-same-day takes
 * ({@code linkable}, by event id) and the views ({@link CalendarFilter}) each event is in.
 */
public record CalendarOverview(Instant lastRead, List<CalendarRow> rows, CalendarFilter.Counts counts,
                               Set<String> linkable, Map<String, List<CalendarFilter>> filters) {

    public CalendarOverview {
        rows = List.copyOf(rows);
        linkable = Set.copyOf(linkable);
        filters = Map.copyOf(filters);
    }

    /** The overview of these events on {@code today}. */
    public static CalendarOverview of(Instant lastRead, Collection<CalendarRow> rows, LocalDate today) {
        List<CalendarRow> latestFirst = rows.stream()
                .sorted(Comparator.comparing((CalendarRow r) -> r.event().start()).reversed()).toList();
        Set<String> linkable = CalendarFilter.linkable(latestFirst).stream().map(CalendarRow::eventId)
                .collect(Collectors.toSet());
        Map<String, List<CalendarFilter>> filters = latestFirst.stream().collect(Collectors.toMap(
                CalendarRow::eventId, row -> viewsOf(row, today), (a, b) -> a));
        return new CalendarOverview(lastRead, latestFirst, CalendarFilter.count(latestFirst, today), linkable, filters);
    }

    /** Link-same-day takes the event. */
    public boolean linkable(CalendarRow row) {
        return linkable.contains(row.eventId());
    }

    /** The events link-same-day takes, latest first. */
    public List<CalendarRow> linkableRows() {
        return rows.stream().filter(this::linkable).toList();
    }

    /** The page's views the event is in, in the views' order. */
    public List<CalendarFilter> filters(CalendarRow row) {
        return filters.getOrDefault(row.eventId(), List.of());
    }

    private static List<CalendarFilter> viewsOf(CalendarRow row, LocalDate today) {
        return Arrays.stream(CalendarFilter.values()).filter(f -> f.on(today).test(row)).toList();
    }
}
