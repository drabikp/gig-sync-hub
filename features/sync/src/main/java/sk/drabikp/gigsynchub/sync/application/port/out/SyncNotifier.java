package sk.drabikp.gigsynchub.sync.application.port.out;

import sk.drabikp.gigsynchub.gig.domain.BandId;

/** Tells whoever shows sync state (the pages) that the outbox changed. */
public interface SyncNotifier {

    /** The band's platform work changed. */
    void changed(BandId band);

    /** What every band's pages show changed (a platform held back by its breaker, or resumed). */
    void changedEverywhere();

    /** Tells nobody (tests, or setups without pages). */
    SyncNotifier NONE = new SyncNotifier() {
        @Override
        public void changed(BandId band) {
        }

        @Override
        public void changedEverywhere() {
        }
    };
}
