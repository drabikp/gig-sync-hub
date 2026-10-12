package sk.drabikp.gigsynchub.sync.application;

import org.junit.jupiter.api.Test;
import sk.drabikp.gigsynchub.gig.domain.Gig;
import sk.drabikp.gigsynchub.gig.domain.TestGigs;
import sk.drabikp.gigsynchub.gig.domain.platform.TestPlatforms;
import sk.drabikp.gigsynchub.sync.application.engine.StepRegistry;
import sk.drabikp.gigsynchub.sync.application.engine.SyncDispatcher;
import sk.drabikp.gigsynchub.sync.application.engine.WorkflowEngine;
import sk.drabikp.gigsynchub.sync.application.port.out.SyncStep;
import sk.drabikp.gigsynchub.sync.domain.QueueResult;
import sk.drabikp.gigsynchub.sync.domain.StepType;
import sk.drabikp.gigsynchub.sync.domain.SyncAction;
import sk.drabikp.gigsynchub.sync.domain.SyncStatus;
import sk.drabikp.gigsynchub.sync.domain.SyncTask;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static sk.drabikp.gigsynchub.gig.domain.TestBands.BAND;
import static sk.drabikp.gigsynchub.gig.domain.platform.TestPlatforms.BANDSINTOWN;
import static sk.drabikp.gigsynchub.gig.domain.platform.TestPlatforms.BANDZONE;

/** Queueing rules: a new task replaces failed ones; work a platform's adapters don't take on past gigs is left out. */
class SyncRequestsTest {

    private final SyncFakes.Outbox outbox = new SyncFakes.Outbox();
    private final SyncFakes.Published published = new SyncFakes.Published();
    private final SyncFakes.Gigs gigs = new SyncFakes.Gigs();
    private final SyncFakes.Signals signals = new SyncFakes.Signals();
    private final SyncFakes.MutableClock clock = new SyncFakes.MutableClock();        // 2026-10-08
    /** The platforms' steps: Bandsintown's upload and removal don't take past events; Bandzone takes all. */
    private final SyncFakes.Step bitBulkEdit = new SyncFakes.Step(BANDSINTOWN, StepType.BULK_EDIT, 25);
    private final SyncFakes.Step bitCancel = new SyncFakes.Step(BANDSINTOWN, StepType.CANCEL, 1);
    private final SyncFakes.Step bitRemove = new SyncFakes.Step(BANDSINTOWN, StepType.REMOVE, 1);
    private final SyncFakes.Step bitCreate = new SyncFakes.Step(BANDSINTOWN, StepType.BULK_CREATE, 25);
    private final SyncFakes.Step bzFormEdit = new SyncFakes.Step(BANDZONE, StepType.FORM_EDIT, 1);
    private final SyncFakes.Step bzCancel = new SyncFakes.Step(BANDZONE, StepType.CANCEL, 1);
    private final SyncFakes.Step bzRemove = new SyncFakes.Step(BANDZONE, StepType.REMOVE, 1);
    private final SyncFakes.Step bzCreate = new SyncFakes.Step(BANDZONE, StepType.FORM_CREATE, 1);
    private final WorkflowEngine engine;
    private final SyncRequests requests;
    private final GigPublishingService publishing;

    SyncRequestsTest() {
        for (SyncFakes.Step step : List.of(bitBulkEdit, bitCancel, bitRemove)) {
            step.refusal = gig -> gig.isPast(clock) ? Optional.of("doesn't take past events") : Optional.empty();
        }
        StepRegistry steps = SyncFakes.registry(published, bitBulkEdit, bitCancel, bitRemove, bitCreate, bzFormEdit,
                bzCancel, bzRemove, bzCreate);
        engine = SyncFakes.engine(steps, gigs, published, outbox, signals, clock);
        requests = SyncFakes.requests(outbox, published, signals, clock, steps);
        publishing = new GigPublishingService(requests, new SyncFakes.DirectTransactions(), TestPlatforms.PLATFORMS);
    }

    private final Gig past = onBoth(TestGigs.gig("Summer Fest", "Klub 007"));             // 2026-09-15
    private final Gig upcoming = onBoth(TestGigs.gig("Winter Fest", "Barrák",
            ZonedDateTime.of(2026, 11, 20, 20, 0, 0, 0, ZoneId.of("Europe/Prague"))));

    private Gig onBoth(Gig gig) {
        gigs.save(BAND, gig);
        published.record(BAND, BANDZONE, gig.id(), "bz-" + gig.title());
        published.record(BAND, BANDSINTOWN, gig.id(), "bit-" + gig.title());
        return gig;
    }

    private static Gig renamed(Gig g) {
        return new Gig(g.id(), g.title() + " 2026", g.schedule(), g.location(), g.lineup(), g.admission(), null,
                null, null, null, g.cancelled());
    }

    @Test
    void a_new_publish_replaces_the_failed_one_so_it_stops_waiting_for_the_user() {
        Gig fresh = TestGigs.gig("SNP", "Secret Garden");
        gigs.save(BAND, fresh);
        SyncTask failed = publishing.publish(BAND, Set.of(BANDZONE), List.of(fresh)).queued().getFirst();
        outbox.markRunning(failed.id(), clock.instant());
        outbox.markFailed(failed.id(), "Bandzone doesn't know the city", clock.instant());

        SyncTask again = publishing.publish(BAND, Set.of(BANDZONE), List.of(fresh)).queued().getFirst();

        assertThat(outbox.find(failed.id())).get().satisfies(t -> {
            assertThat(t.status()).isEqualTo(SyncStatus.DISCARDED);
            assertThat(t.message()).contains("replaced", "#" + again.id());
        });
        assertThat(outbox.unfinished(BAND)).extracting(SyncTask::id).containsExactly(again.id());
    }

    @Test
    void an_edit_no_step_of_the_platform_takes_is_not_queued_and_says_why() {
        QueueResult pastEdit = requests.update(BAND, renamed(past));
        QueueResult upcomingEdit = requests.update(BAND, renamed(upcoming));

        assertThat(pastEdit.queued()).extracting(SyncTask::platform).containsExactly(BANDZONE);
        assertThat(pastEdit.notQueued()).singleElement().asString()
                .contains("Summer Fest", "Bandsintown", "by hand", "bulk edit: doesn't take past events",
                        "form edit: none for Bandsintown");
        assertThat(upcomingEdit.queued()).extracting(SyncTask::platform).containsExactly(BANDZONE, BANDSINTOWN);
    }

    @Test
    void cancelling_a_past_gig_is_left_out_where_the_platform_cant_and_deleting_it_forgets_the_copy() {
        QueueResult cancelled = requests.cancel(BAND, past.cancel());
        QueueResult deleted = requests.delete(BAND, past.id(), past);

        assertThat(cancelled.queued()).extracting(SyncTask::platform).containsExactly(BANDZONE);
        assertThat(cancelled.notQueued()).singleElement().asString()
                .contains("Bandsintown", "by hand", "cancel: doesn't take past events");
        assertThat(deleted.queued()).extracting(SyncTask::platform, SyncTask::action)
                .containsExactly(tuple(BANDZONE, SyncAction.DELETE));
        assertThat(deleted.notQueued()).singleElement().asString()
                .contains("Bandsintown", "by hand", "remove: doesn't take past events");
        assertThat(published.isPublished(BAND, BANDSINTOWN, past.id())).as("nothing left to track").isFalse();
    }

    @Test
    void an_edit_queued_before_the_gig_was_over_waits_for_the_user_without_being_tried() {
        SyncRequests everything = SyncFakes.requests(outbox, published, signals, clock);
        gigs.save(BAND, renamed(past));
        SyncTask update = everything.update(BAND, renamed(past)).queued().stream()
                .filter(t -> t.platform().equals(BANDSINTOWN)).findFirst().orElseThrow();
        SyncDispatcher dispatcher = SyncFakes.dispatcher(engine, outbox, signals, clock);

        while (dispatcher.runNext()) {
            // run everything due
        }

        assertThat(outbox.find(update.id())).get().satisfies(t -> {
            assertThat(t.status()).isEqualTo(SyncStatus.FAILED);
            assertThat(t.message()).contains("by hand", "doesn't take past events");
        });
        assertThat(bitBulkEdit.calls).isEmpty();
        assertThat(bzFormEdit.ran()).extracting(SyncStep.Item::externalRef).containsExactly("bz-Summer Fest");
    }
}
