package sk.drabikp.bzscraper.gig.domain.platform;

import java.util.regex.Pattern;

/**
 * One field of a band's account on a platform, as the platform describes it — the page builds the
 * platform's sign-in form from these, so no page or feature needs to know a platform's account.
 *
 * @param name     the field's name (a setting's or a secret's key): letters and digits, e.g. {@code login}
 * @param kind     plain text, a secret (encrypted, never shown again) or a yes/no
 * @param required the account can't be saved without it
 * @param label    what the field is, in the platform's words (English; the page has its own words for
 *                 the common names)
 * @param help     a hint under the field, or null
 */
public record AccountField(String name, Kind kind, boolean required, String label, String help) {

    private static final Pattern NAME = Pattern.compile("[a-z][a-zA-Z0-9]*");

    /** What a field holds. */
    public enum Kind {
        /** Shown as typed (a login, the band's name there). */
        TEXT,
        /** Kept encrypted and never shown again (a password, an authenticator secret). */
        SECRET,
        /** Yes or no ({@code true} / {@code false}). */
        FLAG
    }

    public AccountField {
        if (name == null || !NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("an account field's name is letters and digits: " + name);
        }
        if (kind == null || label == null || label.isBlank()) {
            throw new IllegalArgumentException("an account field needs its kind and label");
        }
        help = help == null || help.isBlank() ? null : help;
    }

    public static AccountField text(String name, boolean required, String label, String help) {
        return new AccountField(name, Kind.TEXT, required, label, help);
    }

    public static AccountField secret(String name, boolean required, String label, String help) {
        return new AccountField(name, Kind.SECRET, required, label, help);
    }

    public static AccountField flag(String name, String label, String help) {
        return new AccountField(name, Kind.FLAG, false, label, help);
    }

    public boolean secret() {
        return kind == Kind.SECRET;
    }
}
