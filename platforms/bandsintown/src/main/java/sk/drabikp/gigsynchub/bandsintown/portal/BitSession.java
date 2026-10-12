package sk.drabikp.gigsynchub.bandsintown.portal;

import sk.drabikp.gigsynchub.bandsintown.BitPortalException;
import sk.drabikp.gigsynchub.bandsintown.RemovalReason;
import sk.drabikp.gigsynchub.gig.domain.Gig;
import sk.drabikp.gigsynchub.importing.domain.ImportedGig;
import sk.drabikp.gigsynchub.sync.application.port.out.FailureKind;
import sk.drabikp.gigsynchub.sync.domain.StepOutcome;

import java.util.List;
import java.util.Optional;

/**
 * An authenticated Bandsintown artist-portal session. Events are addressed by their
 * Bandsintown event id. Bandsintown has no "cancelled" state: a cancelled gig is
 * removed, with "canceled" as the reason.
 */
public interface BitSession extends AutoCloseable {

    /** The most rows one bulk upload takes — the portal's limit. */
    int MAX_ROWS_PER_UPLOAD = 25;

    /**
     * Creates and publishes the gigs. Never throws for a single gig: each result says
     * whether that gig was published (with its new event id) or why not.
     *
     * @param notifyFollowers whether Bandsintown should notify the artist's followers
     * @return one result per gig, in input order
     */
    List<Created> createEvents(List<Gig> gigs, boolean notifyFollowers);

    /**
     * Overwrites several published events in one upload (at most {@link #MAX_ROWS_PER_UPLOAD}),
     * one row per edit in the given order. One result per edit, by position (so even two edits
     * of one event each get theirs); never throws for one event.
     *
     * @throws BitPortalException when the upload as a whole failed (nothing is known per event)
     */
    List<Edited> updateEvents(List<EventEdit> edits) throws BitPortalException;

    /**
     * Changes the one event the way the portal's own edit form does — which also takes PAST events,
     * unlike the upload: the event is read, the gig laid over it and the whole of it put back, place
     * included (the gig's town, as the place search found it, rather than one of Bandsintown's venues).
     *
     * @return a note when Bandsintown placed it far from the town
     * @throws BitPortalException permanent when Bandsintown refused it; needing the user when the gig's
     *                            town was only typed in, so its whereabouts — which the portal insists
     *                            on before it keeps a place — are not known
     */
    Optional<String> editEventInForm(String eventId, Gig gig) throws BitPortalException;

    /** Removes the event from Bandsintown, for {@code reason}. An event that is already gone is not an error. */
    void deleteEvent(String eventId, RemovalReason reason) throws BitPortalException;

    /**
     * Removes the event through its single-page form — which also opens for PAST events, which
     * the event list can't remove. Same reasons as {@link #deleteEvent}; already gone is not an
     * error.
     */
    void deleteEventInForm(String eventId, RemovalReason reason) throws BitPortalException;

    /** Every event of the artist on Bandsintown — upcoming and past — with its event id. */
    List<ImportedGig> listEvents() throws BitPortalException;

    /** Releases the session (closes the browser). Never throws. */
    @Override
    void close();

    /** One published event to overwrite with the gig. */
    record EventEdit(String eventId, Gig gig) {
    }

    /**
     * Outcome of editing one event: updated (with a {@code note} worth showing, e.g. the upload put
     * the place somewhere else and it had to be set from the town), or why not and what that means —
     * REFUSED (Bandsintown's own "no" for the row, e.g. {@code INVALID_START_TIME}), TEMPORARY (the
     * row wasn't applied), NEEDS_USER (a new draft was made instead).
     */
    record Edited(String eventId, String note, FailureKind failure, String error) {

        public static Edited updated(String eventId) {
            return updated(eventId, null);
        }

        public static Edited updated(String eventId, String note) {
            return new Edited(eventId, note, null, null);
        }

        public static Edited notUpdated(String eventId, FailureKind failure, String error) {
            return new Edited(eventId, null, failure, error);
        }

        public boolean isUpdated() {
            return failure == null;
        }

        public StepOutcome outcome() {
            return isUpdated() ? StepOutcome.done(note) : failure.outcome(error);
        }
    }

    /**
     * Outcome of creating one gig: its event id when published (with a {@code note} worth
     * showing, e.g. Bandsintown placed it far from the town), otherwise the reason and what it
     * means — REFUSED (the row made nothing), TEMPORARY, NEEDS_USER (a draft was left there).
     */
    record Created(Gig gig, String eventId, FailureKind failure, String error, String note) {

        public static Created published(Gig gig, String eventId) {
            return new Created(gig, eventId, null, null, null);
        }

        public static Created published(Gig gig, String eventId, String note) {
            return new Created(gig, eventId, null, null, note);
        }

        public static Created failed(Gig gig, FailureKind failure, String error) {
            return new Created(gig, null, failure, error, null);
        }

        public boolean isPublished() {
            return failure == null;
        }

        public StepOutcome outcome() {
            return isPublished() ? StepOutcome.created(eventId, note) : failure.outcome(error);
        }
    }
}
