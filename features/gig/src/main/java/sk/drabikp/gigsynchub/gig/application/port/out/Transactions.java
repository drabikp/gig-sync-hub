package sk.drabikp.gigsynchub.gig.application.port.out;

import java.util.function.Supplier;

/**
 * Runs several port calls as one unit: either all of their changes are stored or none.
 * Keeps transaction management out of the plain application services.
 */
public interface Transactions {

    void inTransaction(Runnable work);

    /** As {@link #inTransaction(Runnable)}, returning the work's result. */
    <T> T computeInTransaction(Supplier<T> work);

    /**
     * As {@link #inTransaction(Runnable)}, for work that may be run TWICE.
     *
     * <p>A unique key another transaction filled first is a LOST RACE, not a fault: both read
     * before either wrote. Where the work's own check already covers the rule the key states — the
     * day and venue a gig is being moved to ({@code gig_match}), the work already waiting for a gig
     * on a platform ({@code sync_task_open_work}) — running it once more is all the answer anyone
     * needs: the re-read finds the winner's row and says what it would have said a moment later
     * ("the day and venue are taken", "already being published").
     *
     * <p>The work must therefore be REPEATABLE: everything in it happens again, so it may add to
     * nothing outside itself. {@code GigImportService#apply} is the counter-example — it tallies
     * into a counter declared outside its transaction, so it uses {@link #inTransaction(Runnable)}
     * and a lost race there is a fault, as it should be.
     */
    void inRepeatableTransaction(Runnable work);

    /** As {@link #inRepeatableTransaction(Runnable)}, returning the work's result. */
    <T> T computeInRepeatableTransaction(Supplier<T> work);
}
