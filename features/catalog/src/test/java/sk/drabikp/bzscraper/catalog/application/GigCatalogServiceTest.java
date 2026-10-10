package sk.drabikp.bzscraper.catalog.application;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.catalog.application.port.in.GigBusyException;
import sk.drabikp.bzscraper.catalog.application.port.in.GigIdentityTakenException;
import sk.drabikp.bzscraper.gig.application.ConcurrentChangeException;
import sk.drabikp.bzscraper.gig.application.NotFoundException;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.TestGigs;
import sk.drabikp.bzscraper.gig.domain.platform.Publication;
import sk.drabikp.bzscraper.sync.application.SyncFakes;
import sk.drabikp.bzscraper.sync.domain.QueueResult;
import sk.drabikp.bzscraper.sync.domain.SyncAction;
import sk.drabikp.bzscraper.sync.domain.SyncStatus;
import sk.drabikp.bzscraper.sync.domain.SyncTask;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static sk.drabikp.bzscraper.gig.domain.TestBands.BAND;
import static sk.drabikp.bzscraper.gig.domain.TestBands.OTHER;
import static sk.drabikp.bzscraper.gig.domain.platform.TestPlatforms.BANDSINTOWN;
import static sk.drabikp.bzscraper.gig.domain.platform.TestPlatforms.BANDZONE;

/** The catalog changes and the platform work they queue (outbox: one transaction). */
class GigCatalogServiceTest {

    private final SyncFakes.Outbox outbox = new SyncFakes.Outbox();
    private final SyncFakes.Published published = new SyncFakes.Published();
    private final SyncFakes.Gigs gigs = new SyncFakes.Gigs();
    private final SyncFakes.Signals signals = new SyncFakes.Signals();
    private final SyncFakes.DirectTransactions transactions = new SyncFakes.DirectTransactions();
    private final SyncFakes.MutableClock clock = new SyncFakes.MutableClock();
    private final CatalogFakes.Moves moves = new CatalogFakes.Moves();
    private final GigCatalogService service = CatalogFakes.catalog(gigs, published, transactions, outbox,
            SyncFakes.requests(outbox, published, signals, clock), moves);

    private final Gig gig = TestGigs.gig("Fest", "Klub 007");

    private Gig onBothPlatforms(Gig g) {
        gigs.save(BAND, g);
        published.record(BAND, BANDZONE, g.id(), "100");
        published.record(BAND, BANDSINTOWN, g.id(), "900");
        return g;
    }

    private static Gig renamed(Gig g, String title) {
        return new Gig(title, g.schedule(), g.location(), g.lineup(), g.admission(), null, null, null, null,
                g.cancelled());
    }

    private List<SyncTask> pending() {
        return outbox.all().stream().filter(t -> t.status() == SyncStatus.PENDING).toList();
    }

    @Test
    void the_same_gig_in_two_bands_is_two_gigs_and_one_bands_changes_leave_the_other_alone() {
        service.add(BAND, gig);
        service.add(OTHER, gig);
        published.record(OTHER, BANDZONE, gig.id(), "999");

        service.cancel(BAND, gig.id());
        QueueResult deleted = service.delete(BAND, gig.id());

        assertThat(service.allGigs(BAND)).isEmpty();
        assertThat(service.gig(OTHER, gig.id()).cancelled()).isFalse();
        assertThat(deleted.queued()).isEmpty();
        assertThat(published.isPublished(OTHER, BANDZONE, gig.id())).isTrue();
        assertThat(service.publicationsByGig(BAND)).isEmpty();
        assertThat(outbox.all()).isEmpty();
    }

    @Test
    void an_edit_queues_an_update_on_every_platform_the_gig_is_on_in_the_same_transaction() {
        onBothPlatforms(gig);

        QueueResult result = service.update(BAND, gig, renamed(gig, "Fest 2026"));

        assertThat(transactions.used).isEqualTo(1);
        assertThat(gigs.findById(BAND, gig.id())).get().extracting(Gig::title).isEqualTo("Fest 2026");
        assertThat(result.queued()).extracting(SyncTask::platform, SyncTask::action)
                .containsExactly(tuple(BANDZONE, SyncAction.UPDATE), tuple(BANDSINTOWN, SyncAction.UPDATE));
        assertThat(signals.wakes).isEqualTo(1);
    }

    @Test
    void an_edit_without_changes_or_of_a_gig_on_no_platform_queues_nothing() {
        onBothPlatforms(gig);
        Gig local = TestGigs.gig("Local only", "Barrák");
        gigs.save(BAND, local);

        assertThat(service.update(BAND, gig, gig).queued()).isEmpty();
        assertThat(service.update(BAND, local, renamed(local, "Renamed")).queued()).isEmpty();
        assertThat(signals.wakes).isZero();
    }

    @Test
    void edits_made_before_the_update_ran_are_carried_by_that_one_update() {
        onBothPlatforms(gig);

        Gig first = renamed(gig, "Fest 2026");
        service.update(BAND, gig, first);
        service.update(BAND, first, renamed(gig, "Fest 2026 — sold out"));

        assertThat(pending()).hasSize(2);
    }

    @Test
    void a_gig_whose_platform_work_is_running_is_not_changed() {
        onBothPlatforms(gig);
        Gig edited = renamed(gig, "Fest 2026");
        SyncTask first = service.update(BAND, gig, edited).queued().getFirst();
        outbox.markRunning(first.id(), clock.instant());

        assertThatThrownBy(() -> service.update(BAND, edited, renamed(gig, "Fest 2026 — sold out")))
                .isInstanceOf(GigBusyException.class);
        assertThatThrownBy(() -> service.delete(BAND, edited.id())).isInstanceOf(GigBusyException.class);
        assertThatThrownBy(() -> service.cancel(BAND, edited.id())).isInstanceOf(GigBusyException.class);
        assertThat(gigs.findById(BAND, gig.id())).contains(edited);
    }

    @Test
    void a_link_is_forgotten_through_the_write_path_unless_the_gigs_platform_work_is_running() {
        CatalogWrites writes = CatalogFakes.writes(gigs, published, outbox,
                SyncFakes.requests(outbox, published, signals, clock));
        onBothPlatforms(gig);

        writes.forgetLink(BAND, BANDZONE, gig.id());
        assertThat(published.isPublished(BAND, BANDZONE, gig.id())).isFalse();

        SyncTask running = service.update(BAND, gig, renamed(gig, "Fest 2026")).queued().getFirst();
        outbox.markRunning(running.id(), clock.instant());
        assertThatThrownBy(() -> writes.forgetLink(BAND, BANDSINTOWN, gig.id()))
                .isInstanceOfSatisfying(GigBusyException.class,
                        e -> assertThat(e.getMessage()).contains("Fest 2026").doesNotContain("GigId["));
        assertThat(published.isPublished(BAND, BANDSINTOWN, gig.id())).isTrue();
    }

    @Test
    void a_new_gig_or_an_edit_onto_another_gigs_day_and_venue_is_refused() {
        Gig other = TestGigs.gig("Other", "Lucerna");
        gigs.save(BAND, gig);
        gigs.save(BAND, other);
        Gig movedOntoOther = new Gig(gig.title(), other.schedule(), other.location(), gig.lineup(), gig.admission(),
                null, null, null, null, false);

        assertThatThrownBy(() -> service.add(BAND, TestGigs.gig("Same place", "Klub 007")))
                .isInstanceOf(GigIdentityTakenException.class);
        assertThatThrownBy(() -> service.update(BAND, gig, movedOntoOther)).isInstanceOf(GigIdentityTakenException.class);
        assertThat(gigs.findById(BAND, other.id())).contains(other);
        assertThat(gigs.findById(BAND, gig.id())).contains(gig);
    }

    @Test
    void an_edit_that_moves_the_gigs_identity_takes_its_records_and_pending_work_along_and_says_so() {
        onBothPlatforms(gig);
        Gig edited = renamed(gig, "Fest 2026");
        service.update(BAND, gig, edited);
        Gig moved = TestGigs.gig("Fest 2026", "Klub 007",
                ZonedDateTime.of(2026, 10, 1, 20, 0, 0, 0, ZoneId.of("Europe/Prague")));

        service.update(BAND, edited, moved);

        assertThat(gigs.findById(BAND, gig.id())).isEmpty();
        assertThat(published.externalRef(BAND, BANDZONE, moved.id())).contains("100");
        assertThat(moves.moved).containsExactly(edited.id() + " -> " + moved.id());
        assertThat(pending()).hasSize(2).allSatisfy(t -> {
            assertThat(t.gigId()).isEqualTo(moved.id());
            assertThat(t.gigLabel()).startsWith("2026-10-01");
        });
    }

    @Test
    void an_edit_of_a_gig_changed_or_deleted_meanwhile_is_refused_and_changes_nothing() {
        onBothPlatforms(gig);
        Gig other = renamed(gig, "Changed in another window");
        service.update(BAND, gig, other);
        int queuedBefore = outbox.all().size();

        assertThatThrownBy(() -> service.update(BAND, gig, renamed(gig, "My stale edit")))
                .isInstanceOf(ConcurrentChangeException.class).hasMessageContaining("changed meanwhile");
        assertThat(gigs.findById(BAND, gig.id())).get().extracting(Gig::title).isEqualTo("Changed in another window");
        assertThat(outbox.all()).hasSize(queuedBefore);

        service.delete(BAND, gig.id());
        assertThatThrownBy(() -> service.update(BAND, other, renamed(other, "Too late")))
                .isInstanceOf(ConcurrentChangeException.class).hasMessageContaining("deleted meanwhile");
        assertThat(gigs.findById(BAND, gig.id())).isEmpty();
    }

    @Test
    void cancel_and_reactivate_queue_the_same_on_the_platforms() {
        onBothPlatforms(gig);

        QueueResult cancelled = service.cancel(BAND, gig.id());
        outbox.all().forEach(t -> outbox.markDone(t.id(), null, clock.instant()));
        QueueResult reactivated = service.reactivate(BAND, gig.id());

        assertThat(cancelled.queued()).extracting(SyncTask::action).containsOnly(SyncAction.CANCEL).hasSize(2);
        assertThat(reactivated.queued()).extracting(SyncTask::action).containsOnly(SyncAction.REACTIVATE).hasSize(2);
        assertThat(gigs.findById(BAND, gig.id())).get().extracting(Gig::cancelled).isEqualTo(false);
    }

    @Test
    void a_cancel_and_a_reactivate_that_both_have_not_run_cancel_out_into_an_update() {
        onBothPlatforms(gig);

        service.cancel(BAND, gig.id());
        QueueResult reactivated = service.reactivate(BAND, gig.id());

        assertThat(reactivated.queued()).extracting(SyncTask::action).containsOnly(SyncAction.UPDATE);
        assertThat(pending()).extracting(SyncTask::action).containsOnly(SyncAction.UPDATE);
        assertThat(outbox.all()).filteredOn(t -> t.action() == SyncAction.CANCEL)
                .extracting(SyncTask::status).containsOnly(SyncStatus.DISCARDED);
    }

    @Test
    void cancelling_a_cancelled_gig_or_an_unknown_one_changes_nothing() {
        onBothPlatforms(gig.cancel());

        assertThat(service.cancel(BAND, gig.id()).queued()).isEmpty();
        assertThat(service.cancel(BAND, TestGigs.gig("Unknown", "Nowhere").id()).queued()).isEmpty();
        assertThat(outbox.all()).isEmpty();
    }

    @Test
    void delete_discards_pending_work_and_queues_a_delete_where_the_gig_is_while_keeping_its_records() {
        onBothPlatforms(gig);
        service.update(BAND, gig, renamed(gig, "Fest 2026"));

        QueueResult deleted = service.delete(BAND, gig.id());

        assertThat(gigs.findById(BAND, gig.id())).isEmpty();
        assertThat(deleted.queued()).extracting(SyncTask::action).containsOnly(SyncAction.DELETE).hasSize(2);
        assertThat(deleted.queued()).allSatisfy(t -> assertThat(t.gigLabel()).contains("Fest 2026"));
        assertThat(outbox.all()).filteredOn(t -> t.action() == SyncAction.UPDATE)
                .extracting(SyncTask::status).containsOnly(SyncStatus.DISCARDED);
        assertThat(published.externalRef(BAND, BANDSINTOWN, gig.id())).as("kept until the platform delete succeeds")
                .contains("900");
    }

    @Test
    void deleting_a_gig_on_no_platform_queues_nothing() {
        gigs.save(BAND, gig);

        assertThat(service.delete(BAND, gig.id()).queued()).isEmpty();
        assertThat(signals.wakes).isZero();
    }

    @Test
    void publications_are_grouped_by_gig_and_platform() {
        onBothPlatforms(gig);

        assertThat(service.publicationsByGig(BAND).get(gig.id())).containsOnlyKeys(BANDZONE, BANDSINTOWN)
                .containsEntry(BANDZONE, new Publication(BANDZONE, gig.id(), "100"));
    }

    @Test
    void a_gig_is_found_by_its_identity_and_one_that_isnt_there_is_not_found() {
        service.add(BAND, gig);

        assertThat(service.gig(BAND, gig.id())).isEqualTo(gig);
        assertThatThrownBy(() -> service.gig(BAND, TestGigs.gig("Other", "Klub 008").id()))
                .isInstanceOf(NotFoundException.class);
    }
}
