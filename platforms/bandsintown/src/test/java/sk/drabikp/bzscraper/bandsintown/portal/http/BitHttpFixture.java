package sk.drabikp.bzscraper.bandsintown.portal.http;

import com.opencsv.CSVReader;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A stand-in for the artist portal's own API, as the probe of 2026-10-10 saw it: the reply envelope
 * {@code {"status":"OK","payload":…}}, the event lists with the past ones paged by an
 * {@code x-next-page} header, the Bulk Upload answering which spreadsheet row became which event (or
 * refusing a row the way the portal does), and an event's two kinds of write — a PATCH that sets its
 * state (publish, remove) and a PUT that replaces it whole, which is how the portal's own form changes
 * any of its fields. It keeps what it is sent, so the client's read-backs see what was stored, and it
 * insists on the session token and the fingerprint, so a client that forgets them fails here as it
 * would there.
 */
final class BitHttpFixture implements AutoCloseable {

    static final String MANAGER = "1415161";
    static final String ACTOR = "270917";
    static final String TOKEN = "a-session-token";
    static final String FINGERPRINT = "a0b1c2d3";

    private static final Pattern EVENT = Pattern.compile("/api/managers/(\\d+)/managed-actors/(\\d+)/events/(\\d+)");
    private static final Pattern EVENTS = Pattern.compile("/api/managers/(\\d+)/managed-actors/(\\d+)/events");
    private static final Pattern FIELD = Pattern.compile(
            "\"(\\w+)\"\\s*:\\s*(?:\"((?:[^\"\\\\]|\\\\.)*)\"|(null|true|false|-?[\\d.]+))");

    private final HttpServer server;
    private final AtomicInteger ids = new AtomicInteger(109010000);

    /** The events the portal holds, by id, exactly as it would answer them. */
    final Map<String, Map<String, Object>> events = new ConcurrentHashMap<>();
    /** Every spreadsheet uploaded, in order. */
    final List<String> uploads = new CopyOnWriteArrayList<>();
    /** Every change sent to one event: its id, then the fields. */
    final List<Map.Entry<String, String>> changes = new CopyOnWriteArrayList<>();
    /** Every write, as the portal saw it asked: {@code "PUT /api/…/events/1?past=false"}. */
    final List<String> writes = new CopyOnWriteArrayList<>();
    /** How many past events one page holds (the portal's own list gives twenty). */
    volatile int pastPageSize = 20;
    /** A spreadsheet row (2 = the first) the next upload refuses, and what it says. */
    volatile int refusedRow;
    volatile String refusedError = "INVALID_START_TIME";
    /** Whether an upload row carrying an event id wrongly makes a new draft instead of editing it. */
    volatile boolean updatesMakeDrafts;
    /** Whether an uploaded draft refuses to be published (it is then left there for the user). */
    volatile boolean publishFails;

    BitHttpFixture() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::serve);
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    /** An event the portal already holds; {@code fields} overrides any of the defaults. */
    String event(String status, String startDate, Map<String, Object> fields) {
        String id = String.valueOf(ids.incrementAndGet());
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", Long.parseLong(id));
        event.put("status", status);
        event.put("version", "001" + id);
        event.put("start_date", startDate);
        event.put("start_time", "20:00:00");
        event.put("title", "A gig");
        event.put("headline_artist_name", "Eufory");
        event.put("venue_name", "Lucerna Music Bar");
        event.put("venue_city", "Praha");
        event.put("venue_country", "Czechia");
        event.put("venue_timezone", "Europe/Prague");
        event.putAll(fields);
        events.put(id, event);
        return id;
    }

    /** The credentials a test hands the client: this fixture's, with no signing in anywhere. */
    BitCredentials credentials() {
        return new BitCredentials() {
            @Override
            public String sessionToken() {
                return TOKEN;
            }

            @Override
            public String fingerprint() {
                return FINGERPRINT;
            }

            @Override
            public String managerId() {
                return MANAGER;
            }

            @Override
            public String actorId() {
                return ACTOR;
            }
        };
    }

    // ---------------------------------------------------------------- serving

    private void serve(HttpExchange exchange) throws IOException {
        try {
            if (!TOKEN.equals(bearer(exchange)) || !FINGERPRINT.equals(
                    exchange.getRequestHeaders().getFirst("X-fingerprint"))) {
                send(exchange, 401, "{\"status\":\"error\",\"error\":\"unauthorized\"}");
                return;
            }
            URI uri = exchange.getRequestURI();
            Map<String, String> query = query(uri.getRawQuery());
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (!"GET".equals(exchange.getRequestMethod())) {
                writes.add(exchange.getRequestMethod() + " " + uri.getPath()
                        + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery()));
            }
            Matcher one = EVENT.matcher(uri.getPath());
            Matcher list = EVENTS.matcher(uri.getPath());
            if (one.matches()) {
                oneEvent(exchange, one.group(3), body);
            } else if (list.matches() && "PATCH".equals(exchange.getRequestMethod())) {
                upload(exchange, body);
            } else if (list.matches()) {
                listEvents(exchange, query);
            } else {
                send(exchange, 404, "{\"status\":\"error\",\"error\":\"not found\"}");
            }
        } catch (RuntimeException e) {
            send(exchange, 500, "{\"status\":\"error\",\"error\":" + quote(e.toString()) + "}");
        }
    }

    /**
     * One event: read it, set its state by a PATCH (which is also how it is removed), or replace it
     * whole by a PUT — as the portal's own pages do, a PATCH for the state and a PUT for the fields.
     */
    private void oneEvent(HttpExchange exchange, String id, String body) throws IOException {
        Map<String, Object> event = events.get(id);
        if (event == null) {
            send(exchange, 404, "{\"status\":\"error\",\"error\":\"not found\"}");
            return;
        }
        if ("PUT".equals(exchange.getRequestMethod())) {
            changes.add(Map.entry(id, body));
            Map<String, Object> replaced = new LinkedHashMap<>(allFields(body));
            replaced.put("id", event.get("id"));
            replaced.put("version", "001" + System.nanoTime());
            events.put(id, replaced);
            send(exchange, 200, ok(json(replaced)));
            return;
        }
        if ("PATCH".equals(exchange.getRequestMethod())) {
            changes.add(Map.entry(id, body));
            if (publishFails && body.contains("\"PUBLISHED\"")) {
                send(exchange, 200, "{\"status\":\"ERROR\",\"error\":{\"message\":\"cannot publish\"}}");
                return;
            }
            for (Map.Entry<String, String> field : fields(body).entrySet()) {
                if (!field.getKey().equals("version")) {
                    event.put(field.getKey(), field.getValue());
                }
            }
            event.put("version", "001" + System.nanoTime());
        }
        send(exchange, 200, ok(json(event)));
    }

    /**
     * The upcoming or past events. The past ones come newest first, a page at a time; the portal's
     * {@code x-next-page} is when the LAST event of the page starts, and asking again with it as
     * {@code before} leaves that one out (checked against the real portal: no event appears on two pages,
     * and none is skipped).
     */
    private void listEvents(HttpExchange exchange, Map<String, String> query) throws IOException {
        boolean past = Boolean.parseBoolean(query.getOrDefault("past", "false"));
        String before = query.get("before");
        List<Map<String, Object>> matching = events.values().stream()
                .filter(event -> !"DELETED".equals(event.get("status")))
                .filter(event -> past == String.valueOf(event.get("start_date")).compareTo("2026-10-10") < 0)
                .sorted((a, b) -> startOf(b).compareTo(startOf(a)))
                .filter(event -> before == null || startOf(event).compareTo(before) < 0)
                .toList();
        int to = Math.min(pastPageSize, matching.size());
        if (to < matching.size()) {
            exchange.getResponseHeaders().add("x-next-page", startOf(matching.get(to - 1)));
        }
        send(exchange, 200, ok(matching.subList(0, to).stream().map(BitHttpFixture::json)
                .collect(Collectors.joining(",", "[", "]"))));
    }

    /** When an event starts, as the portal's paging spells a day it has reached. */
    private static String startOf(Map<String, Object> event) {
        String time = event.get("start_time") == null ? "00:00:00" : String.valueOf(event.get("start_time"));
        return event.get("start_date") + "T" + time;
    }

    /**
     * The Bulk Upload: a row without an event id becomes a draft, a row with one edits that event —
     * each answered by its spreadsheet row, as the portal does (its first data row is 2).
     */
    private void upload(HttpExchange exchange, String body) throws IOException {
        String csv = filePart(body);
        uploads.add(csv);
        List<String[]> rows;
        try (CSVReader reader = new CSVReader(new StringReader(csv))) {
            rows = reader.readAll();
        } catch (Exception e) {
            send(exchange, 200, "{\"status\":\"ERROR\",\"error\":{\"message\":" + quote(e.toString()) + "}}");
            return;
        }
        List<String> header = List.of(rows.getFirst());
        int eventIdColumn = header.indexOf("Event Id");
        List<String> drafts = new ArrayList<>();
        List<String> updated = new ArrayList<>();
        for (int row = 1; row < rows.size(); row++) {
            int spreadsheetRow = row + 1;
            if (spreadsheetRow == refusedRow) {
                send(exchange, 200, "{\"status\":\"ERROR\",\"error\":{\"message\":[{\"row\":" + spreadsheetRow
                        + ",\"error\":" + quote(refusedError) + "}]}}");
                return;
            }
            String[] values = rows.get(row);
            String eventId = eventIdColumn < 0 || eventIdColumn >= values.length ? "" : values[eventIdColumn];
            Map<String, Object> fromRow = Map.of(
                    "start_date", cell(header, values, "Start Date* (yyyy-mm-dd)"),
                    "title", cell(header, values, "Event Name"),
                    "venue_name", cell(header, values, "Venue*"),
                    "venue_city", cell(header, values, "City*"));
            if (eventId.isBlank() || updatesMakeDrafts) {
                String id = event("DRAFT", String.valueOf(fromRow.get("start_date")), fromRow);
                drafts.add("{\"row\":" + spreadsheetRow + ",\"id\":" + id + "}");
            } else {
                events.get(eventId).putAll(fromRow);
                updated.add("{\"row\":" + spreadsheetRow + ",\"id\":" + eventId + "}");
            }
        }
        send(exchange, 200, ok("{\"draft_events\":[" + String.join(",", drafts) + "],\"updated_events\":["
                + String.join(",", updated) + "]}"));
    }

    // ---------------------------------------------------------------- plumbing

    private static String bearer(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        return header == null || !header.startsWith("Bearer ") ? null : header.substring("Bearer ".length());
    }

    /** The uploaded file out of the multipart body. */
    private static String filePart(String body) {
        int start = body.indexOf("\r\n\r\n");
        int end = body.lastIndexOf("\r\n--");
        return start < 0 || end <= start ? "" : body.substring(start + 4, end);
    }

    private static String cell(List<String> header, String[] values, String column) {
        int at = header.indexOf(column);
        return at < 0 || at >= values.length ? "" : values[at];
    }

    /** The fields of a small flat JSON body, as text; what was sent as null is left out. */
    private static Map<String, String> fields(String json) {
        Map<String, String> fields = new LinkedHashMap<>();
        Matcher m = FIELD.matcher(json);
        while (m.find()) {
            String value = m.group(2) != null ? m.group(2).replace("\\n", "\n").replace("\\\"", "\"") : m.group(3);
            if (!"null".equals(value)) {
                fields.put(m.group(1), value);
            }
        }
        return fields;
    }

    /**
     * Every field of a small flat JSON body, nulls kept (a PUT clears a field by sending one) and
     * numbers kept as numbers, so a read-back answers them as the portal does.
     */
    private static Map<String, Object> allFields(String json) {
        Map<String, Object> fields = new LinkedHashMap<>();
        Matcher m = FIELD.matcher(json);
        while (m.find()) {
            Object value;
            if (m.group(2) != null) {
                value = m.group(2).replace("\\n", "\n").replace("\\\"", "\"");
            } else if ("null".equals(m.group(3))) {
                value = null;
            } else if ("true".equals(m.group(3)) || "false".equals(m.group(3))) {
                value = m.group(3);
            } else {
                value = Double.valueOf(m.group(3));
            }
            fields.put(m.group(1), value);
        }
        return fields;
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> query = new LinkedHashMap<>();
        if (raw != null) {
            for (String pair : raw.split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    query.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                            URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
                }
            }
        }
        return query;
    }

    private static String ok(String payload) {
        return "{\"status\":\"OK\",\"payload\":" + payload + "}";
    }

    private static String json(Map<String, ?> values) {
        return values.entrySet().stream()
                .map(e -> quote(e.getKey()) + ":" + (e.getValue() instanceof Number || e.getValue() == null
                        ? String.valueOf(e.getValue()) : quote(String.valueOf(e.getValue()))))
                .collect(Collectors.joining(",", "{", "}"));
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
