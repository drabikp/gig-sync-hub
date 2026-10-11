package sk.drabikp.bzscraper.calendar.domain.rules;

import org.jmolecules.ddd.annotation.ValueObject;
import sk.drabikp.bzscraper.gig.domain.TextFold;

import java.util.Arrays;
import java.util.List;

/**
 * One rule of a band profile: a {@link RuleKind}, its value (words, a number or a
 * {@code label=prefix} field, depending on the kind), the weight it adds to an event's
 * score when it matches (0 for role kinds) and where it came from.
 */
@ValueObject
public record ProfileRule(RuleKind kind, String value, int weight, RuleOrigin origin, boolean enabled) {

    public ProfileRule {
        if (kind == null || origin == null) {
            throw new IllegalArgumentException("rule kind and origin are required");
        }
        value = value == null || value.isBlank() ? null : value.strip();
        switch (kind.value()) {
            case NONE -> value = null;
            case TEXT -> {
                if (value == null || alternativesOf(value).isEmpty()) {
                    throw new IllegalArgumentException(kind + " needs one or more words");
                }
            }
            case NUMBER -> {
                if (value == null || !value.matches("\\d+")) {
                    throw new IllegalArgumentException(kind + " needs a whole number");
                }
            }
            case FIELD -> {
                if (value == null || !value.matches("[^=]+=[^=]+")) {
                    throw new IllegalArgumentException(kind + " needs label=prefix");
                }
            }
        }
        if (!kind.weighted()) {
            weight = 0;
        }
    }

    public static ProfileRule preset(RuleKind kind, String value, int weight) {
        return new ProfileRule(kind, value, weight, RuleOrigin.PRESET, true);
    }

    /** The value's alternatives, folded for comparison (see {@link sk.drabikp.bzscraper.gig.domain.TextFold#fold}). */
    public List<String> alternatives() {
        return value == null ? List.of() : alternativesOf(value);
    }

    public int number() {
        return Integer.parseInt(value);
    }

    /** For {@link RuleKind.Value#FIELD}: the label and the prefix, folded. */
    public String fieldLabel() {
        return TextFold.fold(value.substring(0, value.indexOf('=')));
    }

    public String fieldPrefix() {
        return TextFold.fold(value.substring(value.indexOf('=') + 1));
    }

    private static List<String> alternativesOf(String value) {
        return Arrays.stream(value.split("\\|")).map(TextFold::fold).filter(s -> !s.isEmpty()).toList();
    }
}
