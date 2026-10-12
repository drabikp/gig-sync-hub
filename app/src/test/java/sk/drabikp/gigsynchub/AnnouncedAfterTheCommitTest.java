package sk.drabikp.gigsynchub;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import sk.drabikp.gigsynchub.catalog.application.port.in.CancelGigUseCase;
import sk.drabikp.gigsynchub.catalog.application.port.in.DeleteGigUseCase;
import sk.drabikp.gigsynchub.catalog.application.port.in.SaveGigUseCase;
import sk.drabikp.gigsynchub.catalog.application.port.in.UpdateGigUseCase;
import sk.drabikp.gigsynchub.gig.application.port.out.LiveUpdates;
import sk.drabikp.gigsynchub.gig.domain.Admission;
import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.gig.domain.Country;
import sk.drabikp.gigsynchub.gig.domain.Gig;
import sk.drabikp.gigsynchub.gig.domain.GigId;
import sk.drabikp.gigsynchub.gig.domain.GigSchedule;
import sk.drabikp.gigsynchub.gig.domain.Location;
import sk.drabikp.gigsynchub.sync.application.port.in.PauseSyncUseCase;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A change is announced only once it is stored. Nothing in the framework arranges that here — there
 * is no after-commit callback anywhere in this app, and deliberately so: a service writes
 * {@code transactions.inTransaction(…)} and then tells the pages, so the transaction's edges are the
 * lambda's braces, visible on the page that decides. What that leaves is a convention, and one line
 * moved INTO the lambda would break it silently: the pages would be told to read a change that the
 * transaction may still roll back, and in the ordinary case they would read it before it is there
 * and show yesterday's answer until something else happened.
 *
 * <p>So this test is the enforcement. The app runs with a {@link LiveUpdates} that asks Spring
 * whether a transaction is open on this thread, which is a question only the real transaction
 * manager can answer — hence the whole context rather than a fake. Every write path the pages
 * follow is driven through it.
 */
@SpringBootTest
class AnnouncedAfterTheCommitTest {

    /** Takes the place of the server-sent events for this context; the announcements still travel. */
    @TestConfiguration
    static class Watching {

        @Bean
        @Primary
        WhenItWasAnnounced whenItWasAnnounced() {
            return new WhenItWasAnnounced();
        }
    }

    static final class WhenItWasAnnounced implements LiveUpdates {

        final List<String> announced = new ArrayList<>();
        final List<String> insideATransaction = new ArrayList<>();

        @Override
        public void changed(BandId band, Topic topic) {
            noted(topic.name());
        }

        @Override
        public void changedEverywhere(Topic topic) {
            noted(topic.name() + " (everyone's)");
        }

        private synchronized void noted(String what) {
            announced.add(what);
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                insideATransaction.add(what);
            }
        }
    }

    @Autowired
    private WhenItWasAnnounced announcements;
    @Autowired
    private SaveGigUseCase save;
    @Autowired
    private UpdateGigUseCase update;
    @Autowired
    private CancelGigUseCase cancellation;
    @Autowired
    private DeleteGigUseCase deletion;
    @Autowired
    private PauseSyncUseCase pause;

    @Test
    void no_write_path_tells_the_pages_about_a_change_that_is_not_stored_yet() {
        Gig gig = gig(GigId.fresh(), "announcement order", 20);
        // the same gig, moved a day on: an edit, so it keeps the identity it was added with
        Gig later = gig(gig.id(), "announcement order, later", 21);

        save.add(BandId.FIRST, gig);
        update.update(BandId.FIRST, gig, later);
        cancellation.cancel(BandId.FIRST, gig.id());
        cancellation.reactivate(BandId.FIRST, gig.id());
        deletion.delete(BandId.FIRST, gig.id());
        pause.pause(BandId.FIRST);
        pause.resume(BandId.FIRST);

        assertThat(announcements.insideATransaction)
                .as("announced while a transaction was still open — the pages would read it too early")
                .isEmpty();
        // without this the test would also pass if nothing was announced at all, or if this
        // LiveUpdates never reached the services
        assertThat(announcements.announced).as("every one of those paths announces something")
                .hasSizeGreaterThanOrEqualTo(7);
    }

    private static Gig gig(GigId id, String title, int day) {
        return Gig.create(id, title, GigSchedule.startingAt(
                        ZonedDateTime.of(2028, 3, day, 20, 0, 0, 0, ZoneId.of("Europe/Prague"))),
                new Location("Klub po commite", "Brno", Country.CZECHIA), List.of(), Admission.free(),
                null, null, null, null);
    }
}
