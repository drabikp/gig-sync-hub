package sk.drabikp.gigsynchub.bandsintown.portal;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One reply of the portal's own API — captured in the page by the browser client, or read straight
 * from the answer by the HTTP one: what the
 * portal said to an upload, a save, a delete or a list. Reading its JSON happens here only.
 *
 * @param status the HTTP status, or -1 when the request failed without one
 * @param json   the parsed body, or null when it wasn't JSON
 * @param next   the {@code x-next-page} header of a paged list, or null
 */
public record PortalReply(String method, String url, int status, Map<String, Object> json, String text, String next) {

    private static final int FIRST_DATA_ROW = 2;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> OBJECT = new TypeReference<>() { };

    /**
     * The answer as the HTTP client read it — the body parsed when it is a JSON object, else none
     * (a body that isn't one still shows in {@link #describe()}).
     */
    public static PortalReply of(String method, String url, int status, String body, String nextPage) {
        Map<String, Object> json;
        try {
            json = JSON.readValue(body, OBJECT);
        } catch (JacksonException e) {
            json = null;
        }
        return new PortalReply(method, url, status, json, body, nextPage);
    }

    @SuppressWarnings("unchecked")
    public static PortalReply of(Map<String, ?> captured) {
        return new PortalReply(String.valueOf(captured.get("method")), String.valueOf(captured.get("url")),
                (int) number(captured.get("status")),
                captured.get("json") instanceof Map<?, ?> json ? (Map<String, Object>) json : null,
                String.valueOf(captured.get("text")), BitEvent.text(captured.get("next")));
    }

    public boolean write() {
        return !"GET".equals(method);
    }

    public boolean urlContains(String part) {
        return url.contains(part);
    }

    /** HTTP 200 with {@code "status":"OK"}. */
    public boolean ok() {
        return status == 200 && json != null && "OK".equals(json.get("status"));
    }

    /** Event ids by gig index for new (draft) events; null where a row got no id. */
    public String[] draftIds(int gigCount) {
        return idsByRow(listOf(payloadMap(), "draft_events"), gigCount);
    }

    /** Event ids by gig index for rows that updated an existing event. */
    public String[] updatedIds(int gigCount) {
        return idsByRow(listOf(payloadMap(), "updated_events"), gigCount);
    }

    /** An event list reply ({@code GET …/events?past=…}). */
    public List<BitEvent> events() {
        return payloadList().stream().map(BitEvent::of).toList();
    }

    /** A single-event reply's event (as Bandsintown stored it). */
    public Optional<BitEvent> event() {
        return Optional.ofNullable(payloadMap()).map(BitEvent::of);
    }

    /**
     * The payload exactly as the portal wrote it, when it is one object — every field of an event,
     * including the ones {@link BitEvent} has no use for. The portal's own edit form sends the whole
     * event back when it changes one of its fields, so a change starts from this.
     */
    public Optional<Map<String, Object>> payload() {
        return Optional.ofNullable(payloadMap());
    }

    /** A payload that is a list of objects, as the portal wrote them (its lists of this and that). */
    public List<Map<String, Object>> payloadRows() {
        return payloadList();
    }

    public boolean hasNextPage() {
        return next != null;
    }

    /**
     * The validation errors Bandsintown reports per CSV row ({@code "INVALID_START_TIME"}), in a
     * reply like {@code {"status":"ERROR","error":{"message":[{"row":2,"error":"…"}]}}}; empty
     * when there are none. Such a refusal won't change by trying again.
     */
    public List<String> rowErrors() {
        return errorRows().stream().map(row -> row.get("error")).filter(code -> code != null)
                .map(String::valueOf).distinct().toList();
    }

    /** The row errors by gig index (row 2 = gig 0); null where a row has none. */
    public String[] rowErrorsByIndex(int gigCount) {
        String[] errors = new String[gigCount];
        for (Map<?, ?> row : errorRows()) {
            if (row.get("error") != null) {
                int index = (int) number(row.get("row")) - FIRST_DATA_ROW;
                if (index >= 0 && index < gigCount) {
                    errors[index] = errors[index] == null ? String.valueOf(row.get("error"))
                            : errors[index] + ", " + row.get("error");
                }
            }
        }
        return errors;
    }

    /** Short text of the reply, for error messages. */
    public String describe() {
        return "HTTP " + status + ": " + (text.length() > 300 ? text.substring(0, 300) + "…" : text);
    }

    private List<Map<?, ?>> errorRows() {
        if (json == null || !(json.get("error") instanceof Map<?, ?> error)
                || !(error.get("message") instanceof List<?> rows)) {
            return List.of();
        }
        return rows.stream().filter(row -> row instanceof Map<?, ?>).<Map<?, ?>>map(row -> (Map<?, ?>) row).toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> payloadMap() {
        return json != null && json.get("payload") instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> payloadList() {
        return json != null && json.get("payload") instanceof List<?> list
                ? list.stream().filter(e -> e instanceof Map<?, ?>).map(e -> (Map<String, Object>) e).toList()
                : List.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOf(Map<String, Object> map, String key) {
        return map != null && map.get(key) instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
    }

    private static String[] idsByRow(List<Map<String, Object>> rows, int gigCount) {
        String[] ids = new String[gigCount];
        for (Map<String, Object> row : rows) {
            int index = (int) number(row.get("row")) - FIRST_DATA_ROW;
            if (index >= 0 && index < gigCount && row.get("id") != null) {
                ids[index] = BitEvent.idOf(row.get("id"));
            }
        }
        return ids;
    }

    private static long number(Object value) {
        return value instanceof Number n ? n.longValue() : -1;
    }
}
