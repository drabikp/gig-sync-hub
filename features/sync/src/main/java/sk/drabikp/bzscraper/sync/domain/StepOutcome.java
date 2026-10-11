package sk.drabikp.bzscraper.sync.domain;

import org.jmolecules.ddd.annotation.ValueObject;

/**
 * What one step did with one gig.
 * <ul>
 *   <li>{@link Kind#DONE} — finished; {@code note} is optional; {@code ref} is the new
 *       event's id when the step created one.</li>
 *   <li>{@link Kind#REFUSED} — the platform said a definite "no" to this gig
 *       ({@code INVALID_START_TIME}) and nothing happened there: another step may do it.</li>
 *   <li>{@link Kind#FAILED} — technical (timeout, login lost): try this step again later;
 *       never move on — it may have half-happened.</li>
 *   <li>{@link Kind#FAILED_FOR_GOOD} — trying again won't help and nothing else should try
 *       (e.g. the platform made a new event instead of editing): the user decides.</li>
 *   <li>{@link Kind#POSTPONED} — not tried at all (the platform's browser was busy with a
 *       read): again a little later, no attempt used, not a sign of the platform's health.</li>
 * </ul>
 */
@ValueObject
public record StepOutcome(Kind kind, String note, String ref) {

    public enum Kind { DONE, REFUSED, FAILED, FAILED_FOR_GOOD, POSTPONED }

    public StepOutcome {
        if (kind == null) {
            throw new IllegalArgumentException("outcome kind is required");
        }
        if (kind != Kind.DONE && (note == null || note.isBlank())) {
            throw new IllegalArgumentException("a refusal or failure needs a reason");
        }
    }

    public static StepOutcome done(String note) {
        return new StepOutcome(Kind.DONE, note, null);
    }

    /** A new event exists on the platform under {@code ref} (its id); {@code note} is optional. */
    public static StepOutcome created(String ref, String note) {
        if (ref == null || ref.isBlank()) {
            throw new IllegalArgumentException("a created event needs the platform's id");
        }
        return new StepOutcome(Kind.DONE, note, ref);
    }

    public static StepOutcome refused(String reason) {
        return new StepOutcome(Kind.REFUSED, reason, null);
    }

    public static StepOutcome failed(String error) {
        return new StepOutcome(Kind.FAILED, error, null);
    }

    public static StepOutcome failedForGood(String error) {
        return new StepOutcome(Kind.FAILED_FOR_GOOD, error, null);
    }

    public static StepOutcome postponed(String why) {
        return new StepOutcome(Kind.POSTPONED, why, null);
    }
}
