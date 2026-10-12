package sk.drabikp.gigsynchub.calendar.domain.rules;

import org.jmolecules.ddd.annotation.ValueObject;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * How a label is found in an event's notes (folded, {@code TextFold}): {@code label:} at the start of
 * a word, the spaces inside the label optional ("Čas predbežne" / "Časpredbežne"). The patterns are
 * built once per label — a band has a handful, the calendar many events.
 */
@ValueObject
public final class NotesLabels {

    private static final String WORD_START = "(?<![\\p{L}\\p{N}])";
    private static final Map<String, Pattern> PATTERNS = new ConcurrentHashMap<>();

    private NotesLabels() {
    }

    /** {@code label:} */
    public static Pattern label(String folded) {
        return pattern(folded, "\\s*:");
    }

    /** {@code label: value} — the value (up to a space) is group 1. */
    public static Pattern field(String folded) {
        return pattern(folded, "\\s*:[ \\t]*(\\S+)");
    }

    /** {@code label: 19:30} (or {@code 19.30}) — hour and minute are groups 1 and 2. */
    public static Pattern time(String folded) {
        return pattern(folded, "\\s*:\\s*(\\d{1,2})[:.](\\d{2})");
    }

    private static Pattern pattern(String folded, String after) {
        return PATTERNS.computeIfAbsent(after + "\u0000" + folded,
                key -> Pattern.compile(WORD_START + spaced(folded) + after));
    }

    private static String spaced(String folded) {
        return Arrays.stream(folded.split(" ")).map(Pattern::quote).collect(Collectors.joining("\\s*"));
    }
}
