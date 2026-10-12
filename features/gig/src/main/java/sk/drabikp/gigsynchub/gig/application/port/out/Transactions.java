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
}
