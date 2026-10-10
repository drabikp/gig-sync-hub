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
 * @param readAt   where the value is read instead of typed, or null — see {@link ReadAt}
 */
public record AccountField(String name, Kind kind, boolean required, String label, String help, ReadAt readAt) {

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

    /**
     * A value the band cannot simply type, because it only exists inside its own session on the
     * platform's site: the page offers {@code script} as something the band runs there (a bookmarklet
     * it keeps, or a line pasted into the browser's console) and the value comes back to be pasted in.
     *
     * <p>This is how a platform whose sign-in is the browser's own — a federated one, or one with
     * two-factor — is connected without this server ever seeing a password: what the band hands over is
     * a session it can revoke. The page knows nothing of the platform; it renders what it is given.
     *
     * @param site   where the band signs in and runs it, an https address
     * @param script one line of JavaScript, which must leave the value on the clipboard or in sight
     */
    public record ReadAt(String site, String script) {
        public ReadAt {
            if (site == null || !site.startsWith("https://") || script == null || script.isBlank()) {
                throw new IllegalArgumentException("a field read at a site needs its https address and a line "
                        + "to run there");
            }
        }
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
        return new AccountField(name, Kind.TEXT, required, label, help, null);
    }

    public static AccountField secret(String name, boolean required, String label, String help) {
        return new AccountField(name, Kind.SECRET, required, label, help, null);
    }

    public static AccountField flag(String name, String label, String help) {
        return new AccountField(name, Kind.FLAG, false, label, help, null);
    }

    /** The same field, with the line that reads its value where the band is signed in ({@link ReadAt}). */
    public AccountField readAt(String site, String script) {
        return new AccountField(name, kind, required, label, help, new ReadAt(site, script));
    }

    public boolean secret() {
        return kind == Kind.SECRET;
    }
}
