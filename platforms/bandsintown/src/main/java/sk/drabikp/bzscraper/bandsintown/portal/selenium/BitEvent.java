package sk.drabikp.bzscraper.bandsintown.portal.selenium;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * An event as the artist portal lists or stores it — the one place its JSON field names are
 * known ({@link #of}); the rest of the adapter works with this. Text fields are null when
 * absent or blank; dates and times are as the portal writes them ("2026-08-29", "21:00:00").
 */
record BitEvent(String id, String status, String startDate, String startTime, String endDate, String endTime,
                String title, String headliner, List<String> lineup, String description,
                String venueName, String venueCity, String venueCountry, String venueTimezone,
                Double latitude, Double longitude) {

    public static BitEvent of(Map<String, ?> raw) {
        return new BitEvent(idOf(raw.get("id")), text(raw.get("status")), text(raw.get("start_date")),
                text(raw.get("start_time")), text(raw.get("end_date")), text(raw.get("end_time")),
                text(raw.get("title")), text(raw.get("headline_artist_name")), names(raw.get("lineup")),
                text(raw.get("description")), text(raw.get("venue_name")), text(raw.get("venue_city")),
                text(raw.get("venue_country")), text(raw.get("venue_timezone")),
                number(raw.get("venue_latitude")), number(raw.get("venue_longitude")));
    }

    public boolean published() {
        return "PUBLISHED".equals(status);
    }

    public boolean deleted() {
        return "DELETED".equals(status);
    }

    public boolean draft() {
        return "DRAFT".equals(status);
    }

    /** The day of the month it starts on (as listed in the portal's rows), or -1. */
    int day() {
        return startDate != null && startDate.length() >= 10 ? Integer.parseInt(startDate.substring(8, 10)) : -1;
    }

    static String idOf(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof Number n ? String.valueOf(n.longValue()) : String.valueOf(value);
    }

    private static List<String> names(Object raw) {
        List<String> names = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                String name = item instanceof Map<?, ?> m ? text(m.get("name")) : text(item);
                if (name != null) {
                    names.add(name);
                }
            }
        }
        return List.copyOf(names);
    }

    private static Double number(Object value) {
        return value instanceof Number n ? n.doubleValue() : null;
    }

    static String text(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() || s.equals("null") ? null : s;
    }
}
