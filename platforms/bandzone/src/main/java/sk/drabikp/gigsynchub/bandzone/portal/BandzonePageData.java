package sk.drabikp.gigsynchub.bandzone.portal;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The data a Bandzone page is rendered from. Since its redesign (seen 2026-11-22) Bandzone is a Next.js
 * site: a page carries what it shows as JSON inside {@code self.__next_f.push([1, "…"])} scripts — the band
 * page its gigs ({@code "gigs":{"upcoming":[…],"past":[…],"countsByYear":[…]}}), a concert's edit page the
 * concert ({@code "gig":{"gigId":…}}). That JSON is what the page's own code reads, so it is read here
 * rather than the markup, whose classes are layout and nothing else.
 *
 * <p>Two things about the values: a date is Prague's wall-clock time written as if it were UTC
 * ({@code "2027-03-15T20:00:00.000Z"} for a concert at 20:00 in Prague — what the concert's form was
 * given, stored as is), and a value React passes on may carry its type as a prefix ({@code "$D2027-…"}).
 */
public final class BandzonePageData {

    /** Where Bandzone's concerts are: what its dates mean. */
    public static final ZoneId PRAGUE = ZoneId.of("Europe/Prague");

    private static final Pattern PUSH = Pattern.compile("self\\.__next_f\\.push\\((\\[.*?])\\)</script>", Pattern.DOTALL);
    /** Reads one value out of the page's data and leaves what follows it be (Jackson 3 would refuse it). */
    private static final JsonMapper JSON = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    private final String data;

    private BandzonePageData(String data) {
        this.data = data;
    }

    /** What the page's scripts carry, in page order. */
    public static BandzonePageData of(String html) {
        StringBuilder data = new StringBuilder();
        Matcher push = PUSH.matcher(html);
        while (push.find()) {
            try {
                JsonNode chunk = JSON.readTree(push.group(1));
                if (chunk.size() > 1 && chunk.get(1).isString()) {
                    data.append(chunk.get(1).asString());
                }
            } catch (JacksonException _) {
                // a script that pushes something other than a chunk of the page's data
            }
        }
        return new BandzonePageData(data.toString());
    }

    /** Whether the page carried any data at all (a page that isn't Next.js carries none). */
    public boolean isEmpty() {
        return data.isEmpty();
    }

    /**
     * The first JSON object that follows {@code "name":} and whose own text starts as {@code start} says
     * ({@code object("gigs", "{\"upcoming\"")}), parsed whole.
     */
    public Optional<JsonNode> object(String name, String start) {
        int at = data.indexOf("\"" + name + "\":" + start);
        if (at < 0) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(JSON.readTree(data.substring(at + name.length() + 3)));
        } catch (JacksonException _) {
            return Optional.empty();
        }
    }

    /** A text value as the page means it: without a type prefix, null when missing, null or blank. */
    public static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asString();
        if (text.startsWith("$D")) {
            text = text.substring(2);
        }
        return text.isBlank() ? null : text;
    }

    /** A date and time as Bandzone writes one (Prague's wall clock, marked UTC), or null. */
    public static ZonedDateTime dateTime(JsonNode node, String field) {
        String text = text(node, field);
        if (text == null) {
            return null;
        }
        try {
            String local = text.endsWith("Z") ? text.substring(0, text.length() - 1) : text;
            return LocalDateTime.parse(local).atZone(PRAGUE);
        } catch (DateTimeParseException _) {
            return null;
        }
    }

    /**
     * How Bandzone writes a date and time it is given: the wall clock, marked UTC
     * ({@code 2027-03-15T20:00:00.000Z}), whatever zone the value came in.
     */
    public static String format(ZonedDateTime when) {
        LocalDateTime local = when.withZoneSameInstant(PRAGUE).toLocalDateTime().withSecond(0).withNano(0);
        return local.toLocalDate() + "T" + local.toLocalTime() + ":00.000Z";
    }
}
