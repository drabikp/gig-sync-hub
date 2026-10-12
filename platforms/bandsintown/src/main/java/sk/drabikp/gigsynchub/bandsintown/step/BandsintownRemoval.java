package sk.drabikp.gigsynchub.bandsintown.step;

import sk.drabikp.gigsynchub.bandsintown.BandsintownPlatform;
import sk.drabikp.gigsynchub.bandsintown.BitPortalException;
import sk.drabikp.gigsynchub.bandsintown.RemovalReason;
import sk.drabikp.gigsynchub.bandsintown.portal.BitPortalClient;
import sk.drabikp.gigsynchub.bandsintown.portal.BitSession;
import sk.drabikp.gigsynchub.gig.domain.Gig;
import sk.drabikp.gigsynchub.gig.domain.platform.Platform;
import sk.drabikp.gigsynchub.sync.application.port.out.OneAtATimeStep;
import sk.drabikp.gigsynchub.sync.domain.StepOutcome;

import java.time.Clock;
import java.util.Optional;

/**
 * Removes Bandsintown events, with a reason. Bandsintown has no cancelled state, so cancelling
 * removes the event too ("the event was canceled"). Two ways, each step's own ({@link #remove}):
 * the Upcoming list's "⋯ → Delete" — which doesn't take past events (they are only listed under
 * Past Events) — and, after it, the event's own form (opened by id; past events too,
 * {@link #takesPast}). One event at a time; already gone = done.
 */
abstract class BandsintownRemoval implements OneAtATimeStep {

    private final BitPortalClient portalClient;
    private final Clock clock;
    private final RemovalReason reason;

    BandsintownRemoval(BitPortalClient portalClient, Clock clock, RemovalReason reason) {
        this.portalClient = portalClient;
        this.clock = clock;
        this.reason = reason;
    }

    /** Removes the event this step's way. */
    abstract void remove(BitSession session, String eventId, RemovalReason reason) throws BitPortalException;

    /** Whether this way reaches past events. */
    abstract boolean takesPast();

    @Override
    public Platform platform() {
        return BandsintownPlatform.PLATFORM;
    }

    @Override
    public Optional<String> refusal(Gig gig) {
        return !takesPast() && gig != null && gig.isPast(clock)
                ? Optional.of("Bandsintown's event list doesn't remove past events")
                : Optional.empty();
    }

    @Override
    public StepOutcome runOne(Item item) {
        try (BitSession session = portalClient.openSession(item.band())) {
            remove(session, item.externalRef(), reason);
            return StepOutcome.done(reason == RemovalReason.CANCELED
                    ? "removed (Bandsintown has no cancelled state)" : null);
        } catch (BitPortalException e) {
            return e.outcome();
        }
    }
}
