package sk.drabikp.gigsynchub.gig.application.port.out;

import sk.drabikp.gigsynchub.gig.domain.BandId;

/**
 * Tells whoever shows a band's state (its open pages) that something changed, so they read it
 * again. Only WHAT changed, and whose, travels ({@link Topic}, the band); the data itself is read
 * through the API.
 * How it is delivered (server-sent events today) is the implementing module's business.
 */
public interface LiveUpdates {

    /** Something of the band's changed; only its pages are told. */
    void changed(BandId band, Topic topic);

    /** Something every band's pages show changed (e.g. a platform held back for everyone). */
    void changedEverywhere(Topic topic);

    /** What changed. */
    enum Topic {
        /** The catalog's gigs. */
        GIGS,
        /** Platform work: queued, running, done or failed; a breaker; the pause. */
        SYNC,
        /** A platform check started, ended or its result changed. */
        CHECK,
        /** Reading the platforms for an import started or ended. */
        IMPORT,
        /** The band calendar was read or a decision about an event changed. */
        CALENDAR
    }

    /** For tests and setups without pages. */
    LiveUpdates NONE = new LiveUpdates() {
        @Override
        public void changed(BandId band, Topic topic) {
        }

        @Override
        public void changedEverywhere(Topic topic) {
        }
    };
}
