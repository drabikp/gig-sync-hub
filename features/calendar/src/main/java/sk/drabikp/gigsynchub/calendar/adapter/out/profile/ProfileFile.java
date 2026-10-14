package sk.drabikp.gigsynchub.calendar.adapter.out.profile;

import sk.drabikp.gigsynchub.calendar.domain.rules.ProfileRule;
import sk.drabikp.gigsynchub.calendar.domain.rules.RuleKind;
import sk.drabikp.gigsynchub.calendar.domain.rules.RuleOrigin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The text format of band-profile rules — the presets shipped in
 * {@code classpath:calendar/*.profile} and the band's own file. One rule per line:
 * <pre>
 * # comment
 * KIND  weight  value
 * TITLE_STARTS_WITH  -6  skuska|reh|cesta
 * LONGER_THAN_DAYS   -3  3
 * REPEATING         -10
 * TRAVEL              0  cesta
 * </pre>
 * The value runs to the end of the line (it may contain spaces); {@code |} separates
 * alternatives. Role kinds take weight 0.
 */
public final class ProfileFile {

    private ProfileFile() {
    }

    /** @throws IllegalArgumentException naming the line, for an unknown kind or a bad weight or value */
    public static List<ProfileRule> parse(String text, RuleOrigin origin) {
        List<ProfileRule> rules = new ArrayList<>();
        String[] lines = text.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\\s+", 3);
            try {
                RuleKind kind = kind(parts[0]);
                if (parts.length < 2) {
                    throw new IllegalArgumentException("a weight is missing");
                }
                rules.add(new ProfileRule(kind, parts.length > 2 ? parts[2] : null, weight(parts[1]), origin, true));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("line " + (i + 1) + ": " + e.getMessage(), e);
            }
        }
        return rules;
    }

    private static RuleKind kind(String name) {
        try {
            return RuleKind.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException _) {
            throw new IllegalArgumentException("unknown rule kind " + name);
        }
    }

    private static int weight(String token) {
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException _) {
            throw new IllegalArgumentException("weight \"" + token + "\" is not a whole number");
        }
    }
}
