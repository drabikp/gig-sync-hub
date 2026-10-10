package sk.drabikp.bzscraper.calendar.adapter.out.ics;

import org.jsoup.Jsoup;
import sk.drabikp.bzscraper.calendar.domain.event.CalendarEvent;
import sk.drabikp.bzscraper.calendar.domain.event.CalendarEventStatus;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Reads the events of an iCalendar (RFC 5545) file — what a Google calendar's iCal
 * address returns. Only what the classifier needs: title, location, notes (HTML turned
 * into text), start/end in the band's time zone, all-day, repeating, shown-as-free,
 * status, last modified. Repeating events are not expanded: the series is one event,
 * marked repeating, and each changed occurrence is another (id = uid + its original start).
 */
final class IcsParser {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final Pattern HTML_TAG = Pattern.compile("<(br|p|div|b|i|u|span|a|ul|ol|li)\\b[^>]*>|&nbsp;",
            Pattern.CASE_INSENSITIVE);

    private IcsParser() {
    }

    /** One content line: name, parameters (upper-cased names) and the raw value. */
    private record Property(String name, Map<String, String> params, String value) {
    }

    /**
     * @param fallbackZone the band's zone, used when the calendar doesn't name one
     *                     ({@code X-WR-TIMEZONE}); event times are converted to the calendar's zone
     */
    static List<CalendarEvent> parse(String ics, ZoneId fallbackZone) {
        List<String> lines = unfold(ics);
        ZoneId zone = lines.stream().map(IcsParser::property)
                .filter(p -> p != null && p.name().equals("X-WR-TIMEZONE"))
                .findFirst().map(p -> zoneOr(p.value(), fallbackZone)).orElse(fallbackZone);

        List<CalendarEvent> events = new ArrayList<>();
        Map<String, Property> current = null;
        int nested = 0;
        for (String line : lines) {
            Property p = property(line);
            if (p == null) {
                continue;
            }
            if (p.name().equals("BEGIN")) {
                if (p.value().equalsIgnoreCase("VEVENT")) {
                    current = new HashMap<>();
                } else if (current != null) {
                    nested++;                       // VALARM etc. inside the event
                }
            } else if (p.name().equals("END")) {
                if (p.value().equalsIgnoreCase("VEVENT") && current != null) {
                    toEvent(current, zone).ifPresent(events::add);
                    current = null;
                    nested = 0;
                } else if (current != null && nested > 0) {
                    nested--;
                }
            } else if (current != null && nested == 0) {
                current.putIfAbsent(p.name(), p);
            }
        }
        return events;
    }

    private static Optional<CalendarEvent> toEvent(Map<String, Property> props, ZoneId zone) {
        Property uid = props.get("UID");
        Property dtStart = props.get("DTSTART");
        if (uid == null || uid.value().isBlank() || dtStart == null) {
            return Optional.empty();
        }
        Property recurrenceId = props.get("RECURRENCE-ID");
        String id = uid.value().strip() + (recurrenceId != null ? "/" + recurrenceId.value().strip() : "");
        try {
            boolean allDay = isDate(dtStart);
            LocalDateTime start = time(dtStart, zone);
            Property dtEnd = props.get("DTEND");
            LocalDateTime end = dtEnd != null ? time(dtEnd, zone) : allDay ? start.plusDays(1) : start;
            return Optional.of(new CalendarEvent(id,
                    text(props.get("SUMMARY")), text(props.get("LOCATION")), notes(props.get("DESCRIPTION")),
                    start, end, allDay,
                    props.containsKey("RRULE") || props.containsKey("RDATE") || recurrenceId != null,
                    has(props, "TRANSP", "TRANSPARENT"),
                    has(props, "STATUS", "CANCELLED") ? CalendarEventStatus.CANCELLED
                            : has(props, "STATUS", "TENTATIVE") ? CalendarEventStatus.TENTATIVE
                            : CalendarEventStatus.CONFIRMED,
                    lastModified(props.get("LAST-MODIFIED"))));
        } catch (DateTimeException e) {
            return Optional.empty();      // an event the calendar wrote oddly is left out, not fatal
        }
    }

    private static boolean isDate(Property p) {
        return "DATE".equalsIgnoreCase(p.params().get("VALUE")) || p.value().strip().length() == 8;
    }

    private static LocalDateTime time(Property p, ZoneId zone) {
        String v = p.value().strip();
        if (isDate(p)) {
            return LocalDate.parse(v.substring(0, 8), DATE).atStartOfDay();
        }
        if (v.endsWith("Z")) {
            return LocalDateTime.parse(v.substring(0, 15), DATE_TIME).atOffset(ZoneOffset.UTC)
                    .atZoneSameInstant(zone).toLocalDateTime();
        }
        LocalDateTime local = LocalDateTime.parse(v.substring(0, 15), DATE_TIME);
        String tzid = p.params().get("TZID");
        return tzid == null ? local : local.atZone(zoneOr(tzid, zone)).withZoneSameInstant(zone).toLocalDateTime();
    }

    private static Instant lastModified(Property p) {
        if (p == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(p.value().strip().substring(0, 15), DATE_TIME).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException | StringIndexOutOfBoundsException e) {
            return null;
        }
    }

    private static boolean has(Map<String, Property> props, String name, String value) {
        Property p = props.get(name);
        return p != null && p.value().strip().equalsIgnoreCase(value);
    }

    private static ZoneId zoneOr(String id, ZoneId fallback) {
        try {
            return ZoneId.of(id.strip().replace("\"", ""));
        } catch (DateTimeException e) {
            return fallback;
        }
    }

    private static String text(Property p) {
        return p == null ? "" : unescape(p.value());
    }

    /** Google keeps notes written in its editor as HTML; turn them into plain lines. */
    private static String notes(Property p) {
        String text = text(p);
        if (!HTML_TAG.matcher(text).find()) {
            return text;
        }
        String withBreaks = text.replaceAll("(?i)<br\\s*/?>|</?(p|div|li)\\b[^>]*>", "\n");
        return Jsoup.parseBodyFragment(withBreaks).body().wholeText().replace(' ', ' ')
                .replaceAll("[ \\t]+", " ").replaceAll(" *\\n[ \\n]*", "\n").strip();
    }

    private static String unescape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char next = value.charAt(++i);
                out.append(next == 'n' || next == 'N' ? '\n' : next);
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** Joins folded lines (a line starting with a space or tab continues the previous one). */
    private static List<String> unfold(String ics) {
        String normalized = ics.replace("\r\n", "\n").replace('\r', '\n');
        return List.of(normalized.replaceAll("\n[ \\t]", "").split("\n"));
    }

    /** Splits {@code NAME;PARAM=x;PARAM="a:b":value}; colons inside quotes belong to parameters. */
    private static Property property(String line) {
        int colon = -1;
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            } else if (c == ':' && !quoted) {
                colon = i;
                break;
            }
        }
        if (colon <= 0) {
            return null;
        }
        String[] head = line.substring(0, colon).split(";(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
        Map<String, String> params = new HashMap<>();
        for (int i = 1; i < head.length; i++) {
            int eq = head[i].indexOf('=');
            if (eq > 0) {
                params.put(head[i].substring(0, eq).toUpperCase(Locale.ROOT), head[i].substring(eq + 1));
            }
        }
        return new Property(head[0].toUpperCase(Locale.ROOT).strip(), params, line.substring(colon + 1));
    }
}
