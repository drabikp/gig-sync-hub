package sk.drabikp.gigsynchub.catalog.application;

import org.junit.jupiter.api.Test;
import sk.drabikp.gigsynchub.catalog.application.port.in.GigBusyException;
import sk.drabikp.gigsynchub.catalog.application.port.in.GigIdentityTakenException;
import sk.drabikp.gigsynchub.gig.application.ConcurrentChangeException;
import sk.drabikp.gigsynchub.gig.application.NotFoundException;
import sk.drabikp.gigsynchub.gig.domain.Gig;
import sk.drabikp.gigsynchub.gig.domain.GigSchedule;
import sk.drabikp.gigsynchub.gig.domain.TestGigs;
import sk.drabikp.gigsynchub.gig.domain.platform.Publication;
import sk.drabikp.gigsynchub.sync.application.SyncFakes;
import sk.drabikp.gigsynchub.sync.domain.QueueResult;
import sk.drabikp.gigsynchub.sync.domain.SyncAction;
import sk.drabikp.gigsynchub.sync.domain.SyncStatus;
import sk.drabikp.gigsynchub.sync.domain.SyncTask;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static sk.drabikp.gigsynchub.gig.domain.TestBands.BAND;
import static sk.drabikp.gigsynchub.gig.domain.TestBands.OTHER;
import static sk.drabikp.gigsynchub.gig.domain.platform.TestPlatforms.BANDSINTOWN;
import static sk.drabikp.gigsynchub.gig.domain.platform.TestPlatforms.BANDZONE;

/** The catalog changes and the platform work they queue (outbox: one transaction). */
class GigCatalogServiceTest {

    private final SyncFakes.Outbox outbox = new SyncFakes.Outbox();
    private final SyncFakes.Published published = new SyncFakes.Published();
    private final SyncFakes.Gigs gigs = new SyncFakes.Gigs();
    private final SyncFakes.Signals signals = new SyncFakes.Signals();
    private final SyncFakes.DirectTransactions transactions = new SyncFakes.DirectTransactions();
    private final SyncFakes.MutableClock clock = new SyncFakes.MutableClock();
    private final GigCatalogService service = CatalogFakes.catalog(gigs, published, transactions, outbox,
            SyncFakes.requests(outbox, published, signals, clock));

    private final Gig gig = TestGigs.gig("Fest", "Klub 007");

    private Gig onBothPlatforms(Gig g) {
        gigs.save(BAND, g);
        published.record(BAND, BANDZONE, g.id(), "100");
        published.record(BAND, BANDSINTOWN, g.id(), "900");
        return g;
    }

    /** The same gig, called something else — an edit never changes which gig it is. */
    private static Gig renamed(Gig g, String title) {
        return new Gig(g.id(), title, g.schedule(), g.location(), g.lineup(), g.admission(), null, null, null, null,
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
        Gig movedOntoOther = new Gig(gig.id(), gig.title(), other.schedule(), other.location(), gig.lineup(),
                gig.admission(), null, null, null, null, false);

        assertThatThrownBy(() -> service.add(BAND, TestGigs.gig("Same place", "Klub 007")))
                .isInstanceOf(GigIdentityTakenException.class);
        assertThatThrownBy(() -> service.update(BAND, gig, movedOntoOther)).isInstanceOf(GigIdentityTakenException.class);
        assertThat(gigs.findById(BAND, other.id())).contains(other);
        assertThat(gigs.findById(BAND, gig.id())).contains(gig);
    }

    /**
     * The test this replaced said the opposite: that an edit to a gig's day took its platform
     * records and its queued work along to a new identity, and told the listeners. Nothing moves
     * any more — the gig keeps the identity it was created with — so what is left to check is that
     * everything pointing at it is still pointing at it, and that the waiting work says the new day.
     */
    @Test
    void an_edit_that_moves_the_gig_to_another_day_leaves_its_records_and_its_work_where_they_are() {
        onBothPlatforms(gig);
        Gig edited = renamed(gig, "Fest 2026");
        service.update(BAND, gig, edited);
        Gig moved = edited.rescheduledTo(GigSchedule.startingAt(
                ZonedDateTime.of(2026, 10, 1, 20, 0, 0, 0, ZoneId.of("Europe/Prague"))));

        service.update(BAND, edited, moved);

        assertThat(gigs.findById(BAND, gig.id())).as("the same gig, on another day").contains(moved);
        assertThat(moved.match()).isNotEqualTo(gig.match());
        assertThat(published.externalRef(BAND, BANDZONE, gig.id())).contains("100");
        assertThat(pending()).hasSize(2).allSatisfy(t -> {
            assertThat(t.gigId()).isEqualTo(gig.id());
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
