package sk.drabikp.gigsynchub.importing.adapter.in.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import sk.drabikp.gigsynchub.gig.api.RefMapperImpl;
import sk.drabikp.gigsynchub.gig.domain.Gig;
import sk.drabikp.gigsynchub.gig.domain.TestGigs;
import sk.drabikp.gigsynchub.importing.adapter.in.rest.api.ImportDecisionRequest;
import sk.drabikp.gigsynchub.importing.adapter.in.rest.api.ImportPlanResponse;
import sk.drabikp.gigsynchub.importing.application.port.in.ImportSessionUseCase.Choice;
import sk.drabikp.gigsynchub.importing.domain.ImportPlan;
import sk.drabikp.gigsynchub.importing.domain.ImportProposal;
import sk.drabikp.gigsynchub.importing.domain.ImportedGig;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static sk.drabikp.gigsynchub.gig.domain.platform.TestPlatforms.BANDSINTOWN;
import static sk.drabikp.gigsynchub.gig.domain.platform.TestPlatforms.BANDZONE;

@SpringJUnitConfig(classes = {ImportMapperImpl.class, RefMapperImpl.class})
class ImportMapperTest {

    @Autowired
    ImportMapper mapper;

    private final Gig local = TestGigs.gig("Fest", "Klub 007");
    private final Gig other = TestGigs.gig("Other", "Klub 008");

    @Test
    void the_plans_proposals_are_named_by_their_index_with_their_versions_and_where_they_were_found() {
        ImportPlan plan = new ImportPlan(List.of(
                new ImportProposal(null, List.of(new ImportedGig(BANDZONE, other, "1")), false),
                new ImportProposal(local, List.of(new ImportedGig(BANDSINTOWN, TestGigs.gig("Fest 2", "Klub 007"), "2")),
                        true)),
                3, List.of("skipped one"), Map.of(BANDSINTOWN, "login failed"));

        ImportPlanResponse response = mapper.toResponse(plan);

        assertThat(response.getProposals()).extracting(p -> p.getIndex()).containsExactly(0, 1);
        assertThat(response.getProposals().get(1)).satisfies(p -> {
            assertThat(p.getInCatalog()).isTrue();
            assertThat(p.getHasConflict()).isTrue();
            assertThat(p.getFoundOn()).containsExactly(BANDSINTOWN.id());
            assertThat(p.getVersions()).extracting(v -> v.getPlatform()).containsExactly(null, BANDSINTOWN.id());
            assertThat(p.getVersions().getFirst().getStart()).isEqualTo("2026-09-15T20:00");
        });
        assertThat(response.getAlreadyLinked()).isEqualTo(3);
        assertThat(response.getFailures()).containsEntry(BANDSINTOWN.id(), "login failed");
    }

    @Test
    void the_users_choices_come_in_by_index_and_none_is_no_choice() {
        assertThat(mapper.toChoices(List.of(new ImportDecisionRequest(1, true, false, 2))))
                .containsExactly(new Choice(1, true, false, 2));
        assertThat(mapper.toChoices(null)).isEmpty();
    }
}
