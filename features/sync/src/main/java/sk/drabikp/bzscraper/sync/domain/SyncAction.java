package sk.drabikp.bzscraper.sync.domain;

import org.jmolecules.ddd.annotation.ValueObject;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/** What a sync task does to a gig's copy on one platform. */
@ValueObject
public enum SyncAction {
    PUBLISH,
    UPDATE,
    CANCEL,
    DELETE,
    /** Replace the cancelled copy with an active one (delete + publish again). */
    REACTIVATE;

    /**
     * Safe to repeat after a failure that leaves it unclear what happened: doing it twice
     * changes nothing. Publishing (and reactivating, which publishes) twice would create a
     * second event on the platform, so those are retried only by the user.
     */
    public boolean repeatable() {
        return this == UPDATE || this == CANCEL || this == DELETE;
    }

    /** Every action that is safe to repeat — the set of what {@link #repeatable()} says one by one. */
    public static Set<SyncAction> repeatables() {
        EnumSet<SyncAction> safe = EnumSet.noneOf(SyncAction.class);
        for (SyncAction action : values()) {
            if (action.repeatable()) {
                safe.add(action);
            }
        }
        return safe;
    }

    /**
     * The FAILED work that a new one of these takes over, so the user stops being asked about work
     * that is about to be done again anyway: a new publish the failed publish, a new update the
     * failed update, a cancel or a reactivate the failed update as well (it is about to be sent with
     * the newest details), and a delete everything repeatable — an update or a cancel of a gig that
     * is going away is moot. A failed publish or reactivate is never taken over by a delete: it may
     * have created the event on the platform all the same, and only the user can look and say.
     */
    public Set<SyncAction> replaces() {
        return switch (this) {
            case PUBLISH -> EnumSet.of(PUBLISH);
            case UPDATE -> EnumSet.of(UPDATE);
            case CANCEL -> EnumSet.of(CANCEL, UPDATE);
            case REACTIVATE -> EnumSet.of(REACTIVATE, UPDATE);
            case DELETE -> repeatables();
        };
    }

    /**
     * The change that undoes this one as long as the platform has seen neither — a cancel is undone
     * by a reactivate and the other way round, and the platform is then told the gig as it now is
     * instead of both. Nothing else has one: publishing is undone by deleting, but the platform has
     * to be told about that, which is not the same thing.
     */
    public Optional<SyncAction> opposite() {
        return switch (this) {
            case CANCEL -> Optional.of(REACTIVATE);
            case REACTIVATE -> Optional.of(CANCEL);
            default -> Optional.empty();
        };
    }

    public String verb() {
        return switch (this) {
            case PUBLISH -> "publish";
            case UPDATE -> "update";
            case CANCEL -> "cancel";
            case DELETE -> "delete";
            case REACTIVATE -> "reactivate";
        };
    }
}
