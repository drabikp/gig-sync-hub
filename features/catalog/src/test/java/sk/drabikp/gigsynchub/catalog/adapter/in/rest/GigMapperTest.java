package sk.drabikp.gigsynchub.catalog.adapter.in.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import sk.drabikp.gigsynchub.catalog.adapter.in.rest.api.GigResponse;
import sk.drabikp.gigsynchub.catalog.adapter.in.rest.api.PlatformStateValue;
import sk.drabikp.gigsynchub.catalog.adapter.in.rest.api.SyncActionValue;
import sk.drabikp.gigsynchub.gig.api.GigFormMapperImpl;
import sk.drabikp.gigsynchub.gig.api.RefMapperImpl;
import sk.drabikp.gigsynchub.gig.domain.Gig;
import sk.drabikp.gigsynchub.gig.domain.GigDraft;
import sk.drabikp.gigsynchub.gig.domain.TestGigs;
import sk.drabikp.gigsynchub.gig.domain.platform.TestPlatforms;
import sk.drabikp.gigsynchub.sync.domain.PlatformState;
import sk.drabikp.gigsynchub.sync.domain.QueueResult;
import sk.drabikp.gigsynchub.sync.domain.SyncAction;
import sk.drabikp.gigsynchub.sync.domain.SyncStatus;
import sk.drabikp.gigsynchub.sync.domain.SyncTask;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static sk.drabikp.gigsynchub.gig.domain.TestBands.BAND;

/** The generated mapper with the kernel's mappers it uses (as the app wires them). */
@SpringJUnitConfig(classes = {GigMapperImpl.class, GigFormMapperImpl.class, RefMapperImpl.class})
class GigMapperTest {

    @Autowired
    GigMapper mapper;

    private final Gig gig = TestGigs.gig("Fest", "Klub 007");
    private final SyncTask failed = new SyncTask(12, BAND, gig.id(), "Fest", TestPlatforms.BANDSINTOWN, SyncAction.UPDATE,
            SyncStatus.FAILED, 3, Instant.parse("2026-10-09T10:00:00Z"), null, Instant.parse("2026-10-09T10:05:00Z"),
            "refused", null);

    @Test
    void a_gig_answers_with_its_token_form_and_state_on_every_platform() {
        GigView view = new GigView(gig.id(), "rev1", false, GigDraft.of(gig), List.of(
                new GigView.OnPlatform(TestPlatforms.BANDZONE, PlatformState.LIVE, "563406", "https://x/563406",
                        null),
                new GigView.OnPlatform(TestPlatforms.BANDSINTOWN, PlatformState.FAILED, null, null, failed)));

        GigResponse response = mapper.toResponse(view);

        assertThat(response.getId()).isEqualTo(gig.id().token());
        assertThat(response.getGig().getTitle()).isEqualTo("Fest");
        assertThat(response.getPlatforms().getFirst())
                .satisfies(p -> {
                    assertThat(p.getPlatform()).isEqualTo(TestPlatforms.BANDZONE.id());
                    assertThat(p.getRef()).isEqualTo("563406");
                    assertThat(p.getTaskId()).isNull();
                    assertThat(p.getAttempts()).isZero();
                });
        assertThat(response.getPlatforms().get(1))
                .satisfies(p -> {
                    assertThat(p.getState()).isEqualTo(PlatformStateValue.FAILED);
                    assertThat(p.getTaskId()).isEqualTo(12L);
                    assertThat(p.getAction()).isEqualTo(SyncActionValue.UPDATE);
                    assertThat(p.getAttempts()).isEqualTo(3);
                    assertThat(p.getMessage()).isEqualTo("refused");
                });
    }

    @Test
    void queued_work_and_the_history_name_their_task() {
        assertThat(mapper.toResponse(new QueueResult(List.of(failed), List.of("left out: no venue"))))
                .satisfies(q -> {
                    assertThat(q.getQueued().getFirst().getTaskId()).isEqualTo(12L);
                    assertThat(q.getNotQueued()).containsExactly("left out: no venue");
                });
        assertThat(mapper.toHistory(failed).getAt()).isEqualTo(Instant.parse("2026-10-09T10:05:00Z"));
    }
}
