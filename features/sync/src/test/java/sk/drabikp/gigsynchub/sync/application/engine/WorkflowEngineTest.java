package sk.drabikp.gigsynchub.sync.application.engine;

import org.junit.jupiter.api.Test;
import sk.drabikp.gigsynchub.gig.domain.Gig;
import sk.drabikp.gigsynchub.gig.domain.TestGigs;
import sk.drabikp.gigsynchub.gig.domain.platform.Platform;
import sk.drabikp.gigsynchub.sync.application.SyncFakes;
import sk.drabikp.gigsynchub.sync.application.SyncPause;
import sk.drabikp.gigsynchub.sync.application.port.out.SyncStep;
import sk.drabikp.gigsynchub.sync.domain.Lane;
import sk.drabikp.gigsynchub.sync.domain.StepOutcome;
import sk.drabikp.gigsynchub.sync.domain.StepType;
import sk.drabikp.gigsynchub.sync.domain.SyncAction;
import sk.drabikp.gigsynchub.sync.domain.SyncLogEntry;
import sk.drabikp.gigsynchub.sync.domain.SyncStatus;
import sk.drabikp.gigsynchub.sync.domain.SyncTask;
import sk.drabikp.gigsynchub.sync.domain.Workload;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static sk.drabikp.gigsynchub.gig.domain.TestBands.BAND;
import static sk.drabikp.gigsynchub.gig.domain.platform.TestPlatforms.BANDSINTOWN;
import static sk.drabikp.gigsynchub.gig.domain.platform.TestPlatforms.BANDZONE;

/**
 * The workflows run by the engine, the edit one (bulk edit → form edit → by hand) first: ONE call
 * takes a band's due work on a platform through as much of its workflow as this pass allows.
 */
class WorkflowEngineTest {

    private final SyncFakes.Outbox outbox = new SyncFakes.Outbox();
    private final SyncFakes.Published published = new SyncFakes.Published();
    private final SyncFakes.Gigs gigs = new SyncFakes.Gigs();
    private final SyncFakes.Signals signals = new SyncFakes.Signals();
    private final SyncFakes.MutableClock clock = new SyncFakes.MutableClock();        // 2026-10-08

    private final SyncFakes.Step bitBulk = new SyncFakes.Step(BANDSINTOWN, StepType.BULK_EDIT, 25);
    private final SyncFakes.Step bitForm = new SyncFakes.Step(BANDSINTOWN, StepType.FORM_EDIT, 1);
    private final SyncFakes.Step bzForm = new SyncFakes.Step(BANDZONE, StepType.FORM_EDIT, 1);

    WorkflowEngineTest() {
        bitBulk.refusal = gig -> gig.schedule().startDate().isBefore(clock.instant().atZone(ZoneId.of("UTC"))
                .toLocalDate()) ? Optional.of("doesn't take past events") : Optional.empty();
    }

    private WorkflowEngine engine(SyncStep... steps) {
        return SyncFakes.engine(SyncFakes.registry(published, steps), gigs, published, outbox, signals, clock);
    }

    private static Gig upcoming(String title) {
        return TestGigs.gig(title, "Klub " + title, ZonedDateTime.of(2026, 11, 20, 20, 0, 0, 0,
                ZoneId.of("Europe/Prague")));
    }

    private SyncTask edit(Gig gig, Platform platform) {
        gigs.save(BAND, gig);
        published.record(BAND, platform, gig.id(), "id-" + gig.title());
        return outbox.enqueue(BAND, gig.id(), SyncTask.labelOf(gig), platform, SyncAction.UPDATE, clock.instant());
    }

    private SyncTask after(SyncTask task) {
        return outbox.find(task.band(), task.id()).orElseThrow();
    }

    /** What the worker does: the next due task names a workload, over and over until none is due. */
    private void runAll(WorkflowEngine engine) {
        Optional<SyncTask> next;
        while ((next = outbox.nextDue(clock.instant())).isPresent()) {
            engine.run(Workload.of(next.get()));
        }
    }

    @Test
    void a_step_that_throws_fails_its_runs_with_what_was_thrown_and_reports_the_fault_in_full() {
        SyncTask task = edit(upcoming("A"), BANDZONE);
        bzForm.outcome = item -> {
            throw new NullPointerException();
        };
        SyncFakes.Faults faults = new SyncFakes.Faults();
        WorkflowEngine engine = SyncFakes.engine(SyncFakes.registry(published, bzForm), gigs, published, outbox,
                signals, clock, new SyncPause(signals, signals, new SyncFakes.Settings()),
                PlatformBreakers.never(clock), faults);

        runAll(engine);

        assertThat(after(task).message()).isEqualTo("form edit: NullPointerException");
        assertThat(faults.reported).singleElement().isInstanceOf(NullPointerException.class);
    }

    @Test
    void due_edits_on_a_platform_go_in_one_batch_and_each_gig_is_settled_on_its_own() {
        List<SyncTask> tasks = new ArrayList<>();
        for (String title : List.of("A", "B", "C")) {
            tasks.add(edit(upcoming(title), BANDSINTOWN));
        }
        bitBulk.outcome = item -> item.gig().title().equals("B") ? StepOutcome.failed("timeout") : StepOutcome.done(null);

        engine(bitBulk, bitForm).run(Workload.of(tasks.getFirst()));

        assertThat(bitBulk.calls).singleElement().satisfies(batch -> assertThat(batch).hasSize(3));
        assertThat(after(tasks.get(0)).status()).isEqualTo(SyncStatus.DONE);
        assertThat(after(tasks.get(0)).message()).isEqualTo("bulk edit");
        assertThat(after(tasks.get(1)).status()).as("tried again at the same step").isEqualTo(SyncStatus.PENDING);
        assertThat(after(tasks.get(1)).nextAttemptAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(1)));
        assertThat(after(tasks.get(1)).step()).isNull();
        assertThat(bitForm.calls).as("a failure never moves on").isEmpty();
    }

    @Test
    void work_that_never_reached_the_platform_runs_again_later_without_using_an_attempt() {
        SyncTask task = edit(upcoming("A"), BANDSINTOWN);
        bitBulk.outcome = item -> StepOutcome.postponed("Interrupted while talking to Bandsintown — again later.");
        List<String> health = new ArrayList<>();
        PlatformHealth watched = new PlatformHealth() {
            @Override
            public boolean held(Lane lane) {
                return false;
            }

            @Override
            public void succeeded(Lane lane) {
                health.add("succeeded");
            }

            @Override
            public void failed(Lane lane, String failure) {
                health.add("failed");
            }
        };
        WorkflowEngine engine = SyncFakes.engine(SyncFakes.registry(published, bitBulk, bitForm), gigs, published,
                outbox, signals, clock, new SyncPause(signals, signals, new SyncFakes.Settings()), watched);

        engine.run(Workload.of(task));

        assertThat(after(task).status()).isEqualTo(SyncStatus.PENDING);
        assertThat(after(task).attempts()).as("it never ran").isZero();
        assertThat(after(task).nextAttemptAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(1)));
        assertThat(health).as("says nothing about the platform").isEmpty();
        assertThat(bitForm.calls).as("not another way: it wasn't refused").isEmpty();

        clock.advance(Duration.ofMinutes(1));
        bitBulk.outcome = item -> StepOutcome.done(null);
        engine.run(Workload.of(task));

        assertThat(after(task).status()).isEqualTo(SyncStatus.DONE);
        assertThat(after(task).attempts()).isEqualTo(1);
    }

    @Test
    void what_the_bulk_step_passes_on_is_done_through_the_form_in_the_same_pass_and_the_path_is_logged() {
        SyncTask past = edit(TestGigs.gig("Summer", "Klub 007"), BANDSINTOWN);           // 2026-09-15
        SyncTask refused = edit(upcoming("Winter"), BANDSINTOWN);
        bitBulk.outcome = item -> StepOutcome.refused("INVALID_START_TIME");

        engine(bitBulk, bitForm).run(Workload.of(past));         // one call: the bulk edit, then the form

        assertThat(bitBulk.ran()).extracting(i -> i.gig().title()).containsExactly("Winter");
        assertThat(bitForm.ran()).extracting(i -> i.gig().title()).containsExactlyInAnyOrder("Summer", "Winter");
        assertThat(List.of(after(past), after(refused))).allSatisfy(t -> {
            assertThat(t.status()).isEqualTo(SyncStatus.DONE);
            assertThat(t.step()).isEqualTo(StepType.FORM_EDIT);
        });
        assertThat(outbox.log(past.band(), past.id())).extracting(SyncLogEntry::message)
                .anySatisfy(m -> assertThat(m).isEqualTo("bulk edit: doesn't take past events → form edit"));
        assertThat(outbox.log(refused.band(), refused.id())).extracting(SyncLogEntry::message)
                .anySatisfy(m -> assertThat(m).isEqualTo("bulk edit: INVALID_START_TIME → form edit"));
    }

    @Test
    void what_no_step_can_do_waits_for_the_user_with_every_reason() {
        SyncTask past = edit(TestGigs.gig("Summer", "Klub 007"), BANDSINTOWN);

        runAll(engine(bitBulk));

        assertThat(after(past).status()).isEqualTo(SyncStatus.FAILED);
        assertThat(after(past).message()).isEqualTo("nothing here can do this on Bandsintown — do it there by hand "
                + "(bulk edit: doesn't take past events; form edit: none for Bandsintown)");
        assertThat(SyncFakes.registry(published, bitBulk).leftOut(BANDSINTOWN, SyncAction.UPDATE, TestGigs.gig("Summer", "Klub 007")))
                .as("said when queueing too").contains(after(past).message());
        assertThat(SyncFakes.registry(published, bitBulk).leftOut(BANDSINTOWN, SyncAction.UPDATE, upcoming("Winter"))).isEmpty();
    }

    @Test
    void a_one_at_a_time_step_takes_a_limited_share_of_a_pass_each_result_saved_on_its_own() {
        List<SyncTask> tasks = new ArrayList<>();
        for (int i = 0; i < WorkflowEngine.ONE_AT_A_TIME_PER_PASS + 2; i++) {
            tasks.add(edit(upcoming("G" + i), BANDZONE));
        }
        WorkflowEngine engine = engine(bzForm);

        engine.run(Workload.of(tasks.getFirst()));      // Bandzone has no bulk edit: its form does them all

        assertThat(tasks).allSatisfy(t -> assertThat(after(t).step()).isEqualTo(StepType.FORM_EDIT));
        assertThat(outbox.log(tasks.getFirst().band(), tasks.getFirst().id())).extracting(SyncLogEntry::message)
                .contains("bulk edit: none for Bandzone → form edit");
        assertThat(bzForm.calls).as("one gig at a time, as many as a pass gives this step")
                .hasSize(WorkflowEngine.ONE_AT_A_TIME_PER_PASS).allSatisfy(c -> assertThat(c).hasSize(1));
        assertThat(tasks).filteredOn(t -> after(t).status() == SyncStatus.PENDING)
                .as("the rest waits at the form, for the next pass").hasSize(2);

        runAll(engine);
        assertThat(tasks).allSatisfy(t -> assertThat(after(t).status()).isEqualTo(SyncStatus.DONE));
    }

    @Test
    void retries_stay_at_the_step_and_end_with_the_user_while_a_definite_failure_ends_at_once() {
        SyncTask flaky = edit(upcoming("A"), BANDZONE);
        bzForm.outcome = item -> StepOutcome.failed("timeout");
        WorkflowEngine engine = engine(bzForm);

        runAll(engine);
        for (int minutes : new int[]{1, 5, 15}) {
            clock.advance(Duration.ofMinutes(minutes));
            runAll(engine);
        }

        assertThat(bzForm.calls).hasSize(4);
        assertThat(after(flaky).status()).isEqualTo(SyncStatus.FAILED);
        assertThat(after(flaky).message()).isEqualTo("form edit: timeout");

        SyncTask unknownCity = edit(upcoming("B"), BANDZONE);
        bzForm.outcome = item -> StepOutcome.failedForGood("Bandzone doesn't know the city");
        runAll(engine);
        assertThat(after(unknownCity).status()).isEqualTo(SyncStatus.FAILED);
        assertThat(after(unknownCity).attempts()).isEqualTo(1);
    }

    @Test
    void edits_are_made_with_the_gig_as_it_is_when_the_step_runs_and_moot_ones_end_done() {
        SyncTask task = edit(upcoming("A"), BANDZONE);
        Gig renamed = new Gig(upcoming("A").id(), "A 2026", upcoming("A").schedule(), upcoming("A").location(),
                List.of(), upcoming("A").admission(), null, null, null, null, false);
        gigs.save(BAND, renamed);
        Gig gone = upcoming("Gone");
        SyncTask goneTask = edit(gone, BANDZONE);
        gigs.deleteById(BAND, gone.id());

        runAll(engine(bzForm));

        assertThat(bzForm.ran()).singleElement().satisfies(i -> {
            assertThat(i.gig()).isEqualTo(renamed);
            assertThat(i.externalRef()).isEqualTo("id-A");
        });
        assertThat(after(task).status()).isEqualTo(SyncStatus.DONE);
        assertThat(after(goneTask).message()).isEqualTo("the gig is no longer in the catalog");
    }

    @Test
    void a_step_type_is_implemented_at_most_once_per_platform() {
        assertThatThrownBy(() -> engine(bzForm, new SyncFakes.Step(BANDZONE, StepType.FORM_EDIT, 1)))
                .isInstanceOf(IllegalStateException.class);
    }

    // --- publish, cancel, delete, reactivate ---

    private final SyncFakes.Step bitCreate = new SyncFakes.Step(BANDSINTOWN, StepType.BULK_CREATE, 25);
    private final SyncFakes.Step bzCreate = new SyncFakes.Step(BANDZONE, StepType.FORM_CREATE, 1);
    private final SyncFakes.Step bzCancel = new SyncFakes.Step(BANDZONE, StepType.CANCEL, 1);
    private final SyncFakes.Step bzRemove = new SyncFakes.Step(BANDZONE, StepType.REMOVE, 1);

    private SyncTask queue(Gig gig, Platform platform, SyncAction action) {
        return outbox.enqueue(BAND, gig.id(), SyncTask.labelOf(gig), platform, action, clock.instant());
    }

    @Test
    void due_publishes_go_in_one_upload_and_each_new_id_is_recorded() {
        Gig a = upcoming("A");
        Gig b = upcoming("B");
        gigs.save(BAND, a);
        gigs.save(BAND, b);
        SyncTask ta = queue(a, BANDSINTOWN, SyncAction.PUBLISH);
        SyncTask tb = queue(b, BANDSINTOWN, SyncAction.PUBLISH);
        bitCreate.outcome = item -> StepOutcome.created("bit-" + item.gig().title(), null);

        runAll(engine(bitCreate));

        assertThat(bitCreate.calls).singleElement().satisfies(batch -> assertThat(batch).hasSize(2));
        assertThat(published.externalRef(BAND, BANDSINTOWN, a.id())).contains("bit-A");
        assertThat(published.externalRef(BAND, BANDSINTOWN, b.id())).contains("bit-B");
        assertThat(List.of(after(ta).status(), after(tb).status())).containsOnly(SyncStatus.DONE);
    }

    @Test
    void a_failed_publish_is_not_tried_again_and_records_nothing_while_moot_ones_end_done() {
        Gig a = upcoming("A");
        gigs.save(BAND, a);
        SyncTask failed = queue(a, BANDZONE, SyncAction.PUBLISH);
        Gig cancelled = upcoming("C").cancel();
        gigs.save(BAND, cancelled);
        SyncTask moot = queue(cancelled, BANDZONE, SyncAction.PUBLISH);
        bzCreate.outcome = item -> StepOutcome.failed("timeout");

        runAll(engine(bzCreate));

        assertThat(after(failed).status()).isEqualTo(SyncStatus.FAILED);
        assertThat(after(failed).message()).contains("create form: timeout", "check Bandzone, then Retry or Discard");
        assertThat(published.isPublished(BAND, BANDZONE, a.id())).isFalse();
        assertThat(after(moot).message()).isEqualTo("the gig was cancelled before it was published");
    }

    @Test
    void cancel_keeps_the_platform_id_and_delete_forgets_it_after_the_gig_left_the_catalog() {
        Gig a = upcoming("A");
        gigs.save(BAND, a.cancel());
        published.record(BAND, BANDZONE, a.id(), "100");
        SyncTask cancel = queue(a, BANDZONE, SyncAction.CANCEL);
        Gig gone = upcoming("Gone");
        published.record(BAND, BANDZONE, gone.id(), "200");
        SyncTask delete = queue(gone, BANDZONE, SyncAction.DELETE);

        runAll(engine(bzCancel, bzRemove));

        assertThat(after(cancel).status()).isEqualTo(SyncStatus.DONE);
        assertThat(published.externalRef(BAND, BANDZONE, a.id())).contains("100");
        assertThat(after(delete).status()).isEqualTo(SyncStatus.DONE);
        assertThat(published.isPublished(BAND, BANDZONE, gone.id())).isFalse();
        assertThat(bzRemove.ran()).singleElement().satisfies(i -> {
            assertThat(i.externalRef()).isEqualTo("200");
            assertThat(i.gig()).isNull();
        });
    }

    @Test
    void reactivating_removes_the_cancelled_copy_then_creates_it_again_from_the_platforms_own_steps() {
        Gig a = upcoming("A");
        gigs.save(BAND, a);
        published.record(BAND, BANDZONE, a.id(), "100");
        SyncTask task = queue(a, BANDZONE, SyncAction.REACTIVATE);
        List<String> order = new ArrayList<>();
        bzRemove.outcome = item -> {
            order.add("remove " + item.externalRef());
            return StepOutcome.done(null);
        };
        bzCreate.outcome = item -> {
            order.add("create");
            return StepOutcome.created("300", null);
        };

        runAll(engine(bzRemove, bzCreate));

        assertThat(order).containsExactly("remove 100", "create");
        assertThat(published.externalRef(BAND, BANDZONE, a.id())).contains("300");
        assertThat(after(task).status()).isEqualTo(SyncStatus.DONE);
        assertThat(after(task).message()).isEqualTo("remove and create again");
    }

    @Test
    void a_failed_recreate_leaves_no_record_so_the_next_publish_creates_it() {
        Gig a = upcoming("A");
        gigs.save(BAND, a);
        published.record(BAND, BANDZONE, a.id(), "100");
        SyncTask task = queue(a, BANDZONE, SyncAction.REACTIVATE);
        bzCreate.outcome = item -> StepOutcome.failed("wizard broke");

        runAll(engine(bzRemove, bzCreate));

        assertThat(published.isPublished(BAND, BANDZONE, a.id())).isFalse();
        assertThat(after(task).status()).isEqualTo(SyncStatus.FAILED);
        assertThat(after(task).message()).contains("re-creating failed (wizard broke)", "publish the gig again");
    }

    @Test
    void a_past_event_the_list_cant_remove_is_cancelled_or_removed_through_its_form() {
        SyncFakes.Step bitCancel = new SyncFakes.Step(BANDSINTOWN, StepType.CANCEL, 1);
        SyncFakes.Step bitFormCancel = new SyncFakes.Step(BANDSINTOWN, StepType.FORM_CANCEL, 1);
        SyncFakes.Step bitRemove = new SyncFakes.Step(BANDSINTOWN, StepType.REMOVE, 1);
        SyncFakes.Step bitFormRemove = new SyncFakes.Step(BANDSINTOWN, StepType.FORM_REMOVE, 1);
        bitCancel.refusal = gig -> Optional.of("the list doesn't remove past events");
        bitRemove.outcome = item -> StepOutcome.refused("a past event — the list can't remove it");
        Gig past = TestGigs.gig("Summer", "Klub 007");                                    // 2026-09-15
        gigs.save(BAND, past.cancel());
        published.record(BAND, BANDSINTOWN, past.id(), "901");
        SyncTask cancel = queue(past, BANDSINTOWN, SyncAction.CANCEL);
        Gig gone = TestGigs.gig("Spring", "Barrák");
        published.record(BAND, BANDSINTOWN, gone.id(), "902");
        SyncTask delete = queue(gone, BANDSINTOWN, SyncAction.DELETE);   // left the catalog: no refusal asked

        runAll(engine(bitCancel, bitFormCancel, bitRemove, bitFormRemove));

        assertThat(bitCancel.ran()).isEmpty();
        assertThat(bitFormCancel.ran()).extracting(SyncStep.Item::externalRef).containsExactly("901");
        assertThat(bitRemove.ran()).extracting(SyncStep.Item::externalRef).containsExactly("902");
        assertThat(bitFormRemove.ran()).extracting(SyncStep.Item::externalRef).containsExactly("902");
        assertThat(List.of(after(cancel).status(), after(delete).status())).containsOnly(SyncStatus.DONE);
        assertThat(published.externalRef(BAND, BANDSINTOWN, past.id())).contains("901");
        assertThat(published.isPublished(BAND, BANDSINTOWN, gone.id())).isFalse();
        assertThat(outbox.log(cancel.band(), cancel.id())).extracting(SyncLogEntry::message).anySatisfy(m ->
                assertThat(m).isEqualTo("cancel: the list doesn't remove past events → cancel in the form"));
        assertThat(outbox.log(delete.band(), delete.id())).extracting(SyncLogEntry::message).anySatisfy(m ->
                assertThat(m).contains("a past event — the list can't remove it", "→ remove in the form"));
    }

    @Test
    void reactivating_removes_the_cancelled_copy_through_the_form_when_the_list_cant() {
        SyncFakes.Step bitRemove = new SyncFakes.Step(BANDSINTOWN, StepType.REMOVE, 1);
        SyncFakes.Step bitFormRemove = new SyncFakes.Step(BANDSINTOWN, StepType.FORM_REMOVE, 1);
        bitRemove.refusal = gig -> Optional.of("the list doesn't remove past events");
        bitCreate.outcome = item -> StepOutcome.created("903", null);
        Gig past = TestGigs.gig("Summer", "Klub 007");
        gigs.save(BAND, past);
        published.record(BAND, BANDSINTOWN, past.id(), "901");
        SyncTask task = queue(past, BANDSINTOWN, SyncAction.REACTIVATE);

        runAll(engine(bitRemove, bitFormRemove, bitCreate));

        assertThat(bitRemove.ran()).isEmpty();
        assertThat(bitFormRemove.ran()).extracting(SyncStep.Item::externalRef).containsExactly("901");
        assertThat(published.externalRef(BAND, BANDSINTOWN, past.id())).contains("903");
        assertThat(after(task).status()).isEqualTo(SyncStatus.DONE);
    }

    @Test
    void a_platform_without_the_steps_for_an_action_leaves_it_to_the_user() {
        Gig a = upcoming("A");
        gigs.save(BAND, a);
        published.record(BAND, BANDSINTOWN, a.id(), "900");
        SyncTask reactivate = queue(a, BANDSINTOWN, SyncAction.REACTIVATE);

        runAll(engine(bitCreate));

        assertThat(after(reactivate).status()).isEqualTo(SyncStatus.FAILED);
        assertThat(after(reactivate).message()).contains("do it there by hand", "remove and create again: none for Bandsintown");
    }

    @Test
    void pausing_lets_the_gig_in_progress_finish_and_leaves_the_rest_queued() {
        List<SyncTask> tasks = new ArrayList<>();
        for (String title : List.of("A", "B", "C")) {
            tasks.add(edit(upcoming(title), BANDZONE));
        }
        SyncPause pause = new SyncPause(signals, signals, new SyncFakes.Settings());
        bzForm.outcome = item -> {
            pause.pause(BAND);                          // the user pauses while the first gig runs
            return StepOutcome.done(null);
        };
        WorkflowEngine engine = SyncFakes.engine(SyncFakes.registry(published, bzForm), gigs, published, outbox, signals,
                clock, pause, PlatformBreakers.never(clock));

        engine.run(Workload.of(tasks.getFirst()));

        assertThat(bzForm.calls).as("the gig in progress finished; nothing else started").hasSize(1);
        assertThat(tasks).extracting(t -> after(t).status())
                .containsExactly(SyncStatus.DONE, SyncStatus.PENDING, SyncStatus.PENDING);

        // a pass cut short leaves each run written down where it got to, so the next one carries on
        assertThat(tasks.subList(1, 3)).allSatisfy(t -> assertThat(after(t).step()).isEqualTo(StepType.FORM_EDIT));
        bzForm.outcome = item -> StepOutcome.done(null);
        pause.resume(BAND);
        runAll(engine);

        assertThat(bzForm.calls).as("two more gigs, and no step done twice").hasSize(3);
        assertThat(tasks).allSatisfy(t -> assertThat(after(t).status()).isEqualTo(SyncStatus.DONE));
    }
}
