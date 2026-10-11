package sk.drabikp.bzscraper.importing.domain;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.gig.domain.Admission;
import sk.drabikp.bzscraper.gig.domain.Country;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.gig.domain.GigSchedule;
import sk.drabikp.bzscraper.gig.domain.Location;
import sk.drabikp.bzscraper.gig.domain.TestGigs;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.gig.domain.platform.Publication;
import sk.drabikp.bzscraper.gig.domain.platform.TestPlatforms;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static sk.drabikp.bzscraper.gig.domain.platform.TestPlatforms.BANDSINTOWN;
import static sk.drabikp.bzscraper.gig.domain.platform.TestPlatforms.BANDZONE;

class ImportPlannerTest {

    private static final ZoneId PRAGUE = ZoneId.of("Europe/Prague");

    private static Gig gig(String title, String venue, String city, int day) {
        return Gig.create(title, GigSchedule.startingAt(ZonedDateTime.of(2026, 9, day, 20, 0, 0, 0, PRAGUE)),
                new Location(venue, city, Country.CZECHIA), List.of(), Admission.free(), null, null, null, null);
    }

    private static ImportedGig on(Platform platform, Gig gig, String ref) {
        return new ImportedGig(platform, gig, ref);
    }

    private static ImportPlan plan(List<Gig> catalog, List<Publication> publications, ImportedGig... imported) {
        return ImportPlanner.plan(catalog, publications, List.of(imported), Map.of(), TestPlatforms.PLATFORMS);
    }

    @Test
    void a_gig_new_to_the_catalog_is_proposed_as_new() {
        Gig fest = gig("Fest", "Klub 007", "Praha", 15);

        ImportPlan plan = plan(List.of(), List.of(), on(BANDZONE, fest, "100"));

        assertThat(plan.proposals()).singleElement().satisfies(p -> {
            assertThat(p.inCatalog()).isFalse();
            assertThat(p.suggested()).isFalse();
            assertThat(p.copies()).extracting(ImportedGig::externalRef).containsExactly("100");
            assertThat(p.hasConflict()).isFalse();
        });
    }

    @Test
    void the_same_gig_on_both_platforms_becomes_one_proposal() {
        Gig bz = gig("Fest", "Klub 007", "Praha", 15);
        Gig bit = gig("Fest", "Klub 007", "Praha", 15);

        ImportPlan plan = plan(List.of(), List.of(), on(BANDSINTOWN, bit, "900"), on(BANDZONE, bz, "100"));

        assertThat(plan.proposals()).singleElement().satisfies(p -> {
            assertThat(p.copies()).extracting(ImportedGig::platform).containsExactly(BANDZONE, BANDSINTOWN);
            assertThat(p.suggested()).isFalse();
        });
    }

    @Test
    void a_platform_gig_with_a_catalog_twin_is_linked_and_differing_details_are_a_conflict() {
        Gig local = gig("Fest", "Klub 007", "Praha", 15);
        Gig renamed = gig("Fest 2026", "Klub 007", "Praha", 15);

        ImportPlan plan = plan(List.of(local), List.of(), on(BANDZONE, renamed, "100"));

        ImportProposal p = plan.proposals().getFirst();
        assertThat(p.local()).isEqualTo(local);
        assertThat(p.hasConflict()).isTrue();
        assertThat(p.versions()).extracting(ImportProposal.Version::platform).containsExactly(null, TestPlatforms.BANDZONE);
        assertThat(p.defaultVersion().platform()).isNull();     // the catalog's details by default
    }

    @Test
    void entry_and_lineup_do_not_count_as_a_difference() {
        Gig bz = Gig.create("Fest", GigSchedule.startingAt(ZonedDateTime.of(2026, 9, 15, 20, 0, 0, 0, PRAGUE)),
                new Location("Klub 007", "Praha", Country.CZECHIA), List.of("Support"), Admission.paid("200 Kč"),
                null, null, null, null);
        Gig bit = gig("Fest", "Klub 007", "Praha", 15);

        ImportPlan plan = plan(List.of(), List.of(), on(BANDZONE, bz, "100"), on(BANDSINTOWN, bit, "900"));

        assertThat(plan.proposals().getFirst().hasConflict()).isFalse();
    }

    @Test
    void an_end_time_on_one_platform_only_is_not_a_difference() {
        Gig bz = gig("Fest", "Klub 007", "Praha", 15);
        Gig bit = new Gig(GigId.fresh(), "Fest", new GigSchedule(bz.schedule().start(), bz.schedule().start().plusHours(6)),
                bz.location(), List.of(), Admission.free(), null, null, null, null, false);

        ImportPlan plan = plan(List.of(), List.of(), on(BANDZONE, bz, "100"), on(BANDSINTOWN, bit, "900"));

        assertThat(plan.proposals().getFirst().hasConflict()).isFalse();
    }

    @Test
    void platform_gigs_already_linked_are_left_alone() {
        Gig local = gig("Fest", "Klub 007", "Praha", 15);

        ImportPlan plan = plan(List.of(local), List.of(new Publication(BANDZONE, local.id(), "100")),
                on(BANDZONE, local, "100"));

        assertThat(plan.proposals()).isEmpty();
        assertThat(plan.alreadyLinked()).isEqualTo(1);
    }

    @Test
    void a_differently_written_venue_on_the_same_day_and_city_is_suggested_as_the_same_gig() {
        Gig bz = gig("Fest", "Lucerna Music Bar", "Praha", 15);
        Gig bit = gig("Fest", "Lucerna", "Praha", 15);

        ImportPlan plan = plan(List.of(), List.of(), on(BANDZONE, bz, "100"), on(BANDSINTOWN, bit, "900"));

        assertThat(plan.proposals()).singleElement().satisfies(p -> {
            assertThat(p.suggested()).isTrue();
            assertThat(p.copies()).hasSize(2);
            assertThat(p.hasConflict()).isTrue();
        });
    }

    @Test
    void a_platform_gig_is_suggested_for_the_catalog_gig_on_that_day_and_city() {
        Gig local = gig("Fest", "Lucerna Music Bar", "Praha", 15);
        Gig bit = gig("Fest", "Lucerna", "Praha", 15);

        ImportPlan plan = plan(List.of(local), List.of(new Publication(BANDZONE, local.id(), "100")),
                on(BANDSINTOWN, bit, "900"));

        assertThat(plan.proposals()).singleElement().satisfies(p -> {
            assertThat(p.local()).isEqualTo(local);
            assertThat(p.suggested()).isTrue();
        });
    }

    @Test
    void two_candidates_on_the_same_day_and_city_give_no_suggestion() {
        Gig early = gig("Matinee", "Klub A", "Praha", 15);
        Gig late = gig("Night", "Klub B", "Praha", 15);
        Gig bit = gig("Night", "Klub B2", "Praha", 15);

        ImportPlan plan = plan(List.of(early, late), List.of(), on(BANDSINTOWN, bit, "900"));

        assertThat(plan.proposals()).singleElement().satisfies(p -> {
            assertThat(p.inCatalog()).isFalse();
            assertThat(p.suggested()).isFalse();
        });
    }

    @Test
    void venue_less_gigs_on_the_same_day_in_different_cities_stay_apart() {
        // live case 2024-06-15: Bandzone "Eufory — TBA, Petřvald" vs Bandsintown "Dobrý festival — TBA, Prešov"
        ImportPlan plan = plan(List.of(), List.of(),
                on(BANDZONE, gig("Eufory", null, "Petřvald", 15), "100"),
                on(BANDSINTOWN, gig("Dobrý festival", null, "Prešov", 15), "900"));

        assertThat(plan.proposals()).hasSize(2).allSatisfy(p -> assertThat(p.copies()).hasSize(1));
        assertThat(plan.skipped()).isEmpty();
    }

    @Test
    void the_english_city_name_on_bandsintown_matches_the_local_one() {
        ImportPlan plan = plan(List.of(), List.of(),
                on(BANDZONE, gig("Tour", "Klub Forum Karlín", "Praha", 15), "100"),
                on(BANDSINTOWN, gig("Tour", "Klub Forum Karlín", "Prague", 15), "900"));

        assertThat(plan.proposals()).singleElement().satisfies(p -> {
            assertThat(p.copies()).hasSize(2);
            assertThat(p.hasConflict()).isFalse();
        });
    }

    @Test
    void another_city_on_the_same_day_is_not_suggested() {
        ImportPlan plan = plan(List.of(), List.of(),
                on(BANDZONE, gig("Fest", "Klub 007", "Praha", 15), "100"),
                on(BANDSINTOWN, gig("Fest", "Fléda", "Brno", 15), "900"));

        assertThat(plan.proposals()).hasSize(2).noneMatch(ImportProposal::suggested);
    }

    @Test
    void a_second_platform_event_with_the_same_date_and_venue_is_skipped_as_a_duplicate() {
        Gig fest = gig("Fest", "Klub 007", "Praha", 15);

        ImportPlan plan = plan(List.of(), List.of(), on(BANDZONE, fest, "100"), on(BANDZONE, fest, "101"));

        assertThat(plan.proposals()).hasSize(1);
        assertThat(plan.skipped()).singleElement().asString().contains("Bandzone");
    }

    @Test
    void a_duplicate_of_an_already_linked_gig_is_skipped_without_an_empty_proposal() {
        // live case: Bandzone lists two concerts on 2016-02-11 at Klub Randal Club
        Gig local = gig("Rock Guitar Show", "Klub Randal Club", "Bratislava", 15);

        ImportPlan plan = plan(List.of(local), List.of(new Publication(BANDZONE, local.id(), "100")),
                on(BANDZONE, local, "100"), on(BANDZONE, gig("Liquid Boogie Roll", "Klub Randal Club", "Bratislava", 15), "101"));

        assertThat(plan.proposals()).isEmpty();
        assertThat(plan.alreadyLinked()).isEqualTo(1);
        assertThat(plan.skipped()).hasSize(1);
    }

    @Test
    void proposals_are_newest_first() {
        ImportPlan plan = plan(List.of(), List.of(),
                on(BANDZONE, TestGigs.gig("Old", "A", ZonedDateTime.of(2020, 1, 1, 20, 0, 0, 0, PRAGUE)), "1"),
                on(BANDZONE, TestGigs.gig("New", "B", ZonedDateTime.of(2027, 1, 1, 20, 0, 0, 0, PRAGUE)), "2"));

        assertThat(plan.proposals()).extracting(p -> p.copies().getFirst().gig().title()).containsExactly("New", "Old");
    }
}
