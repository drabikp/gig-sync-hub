package sk.drabikp.gigsynchub.sync.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowsTest {

    @Test
    void every_action_has_its_steps_in_order_and_each_step_belongs_to_its_action() {
        assertThat(Workflows.of(SyncAction.PUBLISH)).containsExactly(StepType.BULK_CREATE, StepType.FORM_CREATE);
        assertThat(Workflows.of(SyncAction.UPDATE)).containsExactly(StepType.BULK_EDIT, StepType.FORM_EDIT);
        assertThat(Workflows.of(SyncAction.CANCEL)).containsExactly(StepType.CANCEL, StepType.FORM_CANCEL);
        assertThat(Workflows.of(SyncAction.DELETE)).containsExactly(StepType.REMOVE, StepType.FORM_REMOVE);
        assertThat(Workflows.of(SyncAction.REACTIVATE)).containsExactly(StepType.RECREATE);
        for (SyncAction action : SyncAction.values()) {
            assertThat(Workflows.of(action)).as(action.name()).isNotEmpty()
                    .allSatisfy(step -> assertThat(step.action()).isEqualTo(action));
        }
    }

    @Test
    void after_a_step_comes_the_next_way_and_after_the_last_none() {
        assertThat(Workflows.after(StepType.BULK_EDIT)).contains(StepType.FORM_EDIT);
        assertThat(Workflows.after(StepType.CANCEL)).contains(StepType.FORM_CANCEL);
        assertThat(Workflows.after(StepType.FORM_EDIT)).isEmpty();
        assertThat(Workflows.after(StepType.RECREATE)).isEmpty();
    }
}
