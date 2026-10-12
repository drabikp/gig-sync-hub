package sk.drabikp.gigsynchub.gig.application;

import sk.drabikp.gigsynchub.gig.domain.Gig;
import sk.drabikp.gigsynchub.gig.domain.GigDraft;
import sk.drabikp.gigsynchub.gig.domain.GigId;

import java.util.List;
import java.util.Map;

/** A gig form that isn't a gig yet: {@code fields} names what is missing or doesn't fit. */
public class InvalidGigException extends UserFacingException {

    private InvalidGigException(List<String> fields) {
        super(GigErrorCode.INVALID_GIG, Map.of("fields", String.join(",", fields)),
                "Check the gig: " + String.join(", ", fields) + ".");
    }

    /** The draft's gig as a NEW one, or this exception naming what is wrong with it. */
    public static Gig gigOf(GigDraft draft) {
        return gigOf(draft, GigId.fresh());
    }

    /** The draft's gig as an edit of the gig that has this identity, or this exception. */
    public static Gig gigOf(GigDraft draft, GigId keeping) {
        List<GigDraft.Problem> problems = draft.problems();
        if (!problems.isEmpty()) {
            throw new InvalidGigException(problems.stream().map(GigDraft.Problem::field).toList());
        }
        return draft.toGig(keeping);
    }
}
