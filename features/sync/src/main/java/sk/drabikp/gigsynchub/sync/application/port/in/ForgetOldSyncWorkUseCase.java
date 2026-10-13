package sk.drabikp.gigsynchub.sync.application.port.in;

import org.jmolecules.architecture.hexagonal.PrimaryPort;

import java.time.Instant;

/**
 * Letting go of platform work that is over. One row per attempt per gig per platform, and a handful
 * of log lines each, were kept for ever: the outbox said so itself ("tasks are never deleted") and
 * nothing anywhere undid it, so the two tables that grow fastest in this schema had no bound and no
 * plan. A band that syncs three hundred gigs a year leaves tens of thousands of rows behind, every
 * one of them in every nightly dump.
 *
 * <p>Only SETTLED work goes — done and discarded. Failed work waits for the user however long that
 * takes, because it is the one thing in the outbox that still needs them.
 */
@PrimaryPort
public interface ForgetOldSyncWorkUseCase {

    /** Forgets work settled before {@code when}, with its log; answers how many tasks went. */
    int forgetSettledBefore(Instant when);
}
