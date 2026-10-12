package sk.drabikp.gigsynchub.check.adapter.in.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import sk.drabikp.gigsynchub.check.adapter.in.rest.api.CheckStateResponse;
import sk.drabikp.gigsynchub.check.adapter.in.rest.api.DriftFieldValue;
import sk.drabikp.gigsynchub.check.adapter.in.rest.api.DriftKindValue;
import sk.drabikp.gigsynchub.check.domain.Drift;
import sk.drabikp.gigsynchub.check.domain.PlatformCheck;
import sk.drabikp.gigsynchub.gig.api.RefMapperImpl;
import sk.drabikp.gigsynchub.gig.domain.TestGigs;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static sk.drabikp.gigsynchub.gig.domain.platform.TestPlatforms.BANDSINTOWN;
import static sk.drabikp.gigsynchub.gig.domain.platform.TestPlatforms.BANDZONE;

@SpringJUnitConfig(classes = {CheckMapperImpl.class, RefMapperImpl.class})
class CheckMapperTest {

    @Autowired
    CheckMapper mapper;

    @Test
    void before_the_first_check_the_answer_says_so_rather_than_being_empty() {
        CheckStateResponse state = mapper.toResponse(true, null);

        assertThat(state).isNotNull();
        assertThat(state.getRunning()).isTrue();
        assertThat(state.getLast()).isNull();
    }

    @Test
    void a_result_names_gigs_by_token_and_platforms_by_id() {
        var gig = TestGigs.gig("Fest", "Klub 007");
        PlatformCheck check = new PlatformCheck(Instant.parse("2026-10-09T02:30:00Z"),
                List.of(new Drift(BANDZONE, gig.id(), "Fest", "563406", Drift.Kind.DIFFERENT,
                        List.of(new Drift.Difference(Drift.Field.TOWN, "Hranice", "Hranice na Moravě", "town")))),
                Map.of(BANDSINTOWN, "login failed"), Map.of(BANDZONE, 2));

        var last = mapper.toResponse(false, check).getLast();

        assertThat(last.getDrifts().getFirst()).satisfies(d -> {
            assertThat(d.getGigId()).isEqualTo(gig.id().token());
            assertThat(d.getPlatform()).isEqualTo(BANDZONE.id());
            assertThat(d.getKind()).isEqualTo(DriftKindValue.DIFFERENT);
            assertThat(d.getDifferences().getFirst().getField()).isEqualTo(DriftFieldValue.TOWN);
        });
        assertThat(last.getUnreadable()).containsEntry(BANDSINTOWN.id(), "login failed");
        assertThat(last.getUnlinked()).containsEntry(BANDZONE.id(), 2);
    }
}
