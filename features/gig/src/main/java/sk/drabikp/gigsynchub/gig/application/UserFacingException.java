package sk.drabikp.gigsynchub.gig.application;

import java.util.Map;
import java.util.Objects;

/**
 * A rule of the catalog or the sync said no, and nothing was changed; the message is for the
 * user ("The gig was changed meanwhile — …"). Anything else that goes wrong is a fault, not
 * something to show raw. {@link #code()} and {@link #args()} let a page say it in the user's
 * language; the message is the English text and the fallback.
 */
public class UserFacingException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, String> args;

    public UserFacingException(ErrorCode code, Map<String, String> args, String message) {
        this(code, args, message, null);
    }

    public UserFacingException(ErrorCode code, Map<String, String> args, String message, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
        this.args = Map.copyOf(args);
    }

    /** What kind of refusal it is (e.g. {@code gigBusy}). */
    public ErrorCode code() {
        return code;
    }

    /** The values the message is built from, by name. */
    public Map<String, String> args() {
        return args;
    }
}
