package sk.drabikp.gigsynchub.sync.application.port.out;

import sk.drabikp.gigsynchub.sync.domain.StepOutcome;

/**
 * A platform adapter's failure, with what it means for the sync ({@link FailureKind}) — the
 * one failure type the core knows from platforms (adapters may subclass it).
 */
public class PlatformException extends Exception {

    private final FailureKind kind;

    public PlatformException(String message, Throwable cause, FailureKind kind) {
        super(message, cause);
        this.kind = kind;
    }

    public FailureKind kind() {
        return kind;
    }

    /** The step outcome this failure stands for: try again later, try another way, or ask the user. */
    public StepOutcome outcome() {
        return kind.outcome(getMessage());
    }
}
