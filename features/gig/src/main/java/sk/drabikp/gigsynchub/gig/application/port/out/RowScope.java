package sk.drabikp.gigsynchub.gig.application.port.out;

import sk.drabikp.gigsynchub.gig.domain.BandId;

import java.util.function.Supplier;

/**
 * Which band's rows the database lets the work on this thread see and write (row-level security — a net
 * under the ports' own band checks): one band's ({@link #open}), every band's ({@link #everyBand} — the app's
 * own work across bands, asked for by name: the worker picking due work, start-up), or none ({@link #nothing};
 * also when nothing was opened — it fails closed). Scopes nest; each must be closed, innermost first.
 */
public interface RowScope {

    /** An open scope; closing it goes back to the one before. */
    interface Scope extends AutoCloseable {

        @Override
        void close();
    }

    /** From now on (until closed), only {@code band}'s rows. */
    Scope open(BandId band);

    /** From now on (until closed), no band's rows. */
    Scope nothing();

    /** From now on (until closed), every band's rows — the app's own work across bands only, never a request's. */
    Scope everyBand();

    /** Runs {@code work} seeing every band's rows. */
    default <T> T acrossBands(Supplier<T> work) {
        try (Scope ignored = everyBand()) {
            return work.get();
        }
    }

    /** Runs {@code work} seeing only {@code band}'s rows. */
    default <T> T as(BandId band, Supplier<T> work) {
        try (Scope ignored = open(band)) {
            return work.get();
        }
    }

    default void as(BandId band, Runnable work) {
        try (Scope ignored = open(band)) {
            work.run();
        }
    }

    /** No scoping at all (tests without a database). */
    RowScope NONE = new RowScope() {
        @Override
        public Scope open(BandId band) {
            return () -> { };
        }

        @Override
        public Scope nothing() {
            return () -> { };
        }

        @Override
        public Scope everyBand() {
            return () -> { };
        }
    };
}
