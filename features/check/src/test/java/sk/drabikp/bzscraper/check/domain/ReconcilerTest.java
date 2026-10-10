package sk.drabikp.bzscraper.check.domain;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.gig.domain.Address;
import sk.drabikp.bzscraper.gig.domain.Admission;
import sk.drabikp.bzscraper.gig.domain.Country;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.GigSchedule;
import sk.drabikp.bzscraper.gig.domain.Location;
import sk.drabikp.bzscraper.gig.domain.Slot;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.gig.domain.platform.Publication;
import sk.drabikp.bzscraper.gig.domain.platform.TestPlatforms;
import sk.drabikp.bzscraper.importing.domain.ImportedGig;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static sk.drabikp.bzscraper.gig.domain.platform.TestPlatforms.BANDSINTOWN;
import static sk.drabikp.bzscraper.gig.domain.platform.TestPlatforms.BANDZONE;

class ReconcilerTest {

    private static final ZoneId BRATISLAVA = ZoneId.of("Europe/Bratislava");

    private static ZonedDateTime at(int month, int day, int hour, int minute) {
        return ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, BRATISLAVA);
    }

    private static Gig gig(String title, ZonedDateTime start, String venue, String city, Country country) {
        return Gig.create(title, GigSchedule.startingAt(start), new Location(venue, city, country), List.of(),
                Admission.free(), null, null, null, null);
    }

    private static final Gig SNP = gig("SNP - Spolu Na Pódiu Košice", at(8, 29, 21, 0), "Secret Garden (Skrytý Dvor)",
            "Košice", Country.SLOVAKIA);

    private static List<Drift> check(Platform platform, Gig gig, Gig copy) {
        return Reconciler.drifts(TestPlatforms.PLATFORMS.traits(platform), List.of(gig), List.of(new Publication(platform, gig.id(), "1")),
                copy == null ? List.of() : List.of(new ImportedGig(platform, copy, "1")), Set.of());
    }

    @Test
    void platforms_are_compared_by_id_as_the_database_and_the_platforms_give_them_back() {
        Gig renamed = gig("SNP", SNP.schedule().start(), SNP.location().venue(), "Košice", Country.SLOVAKIA);
        Publication stored = new Publication(Platform.of(BANDZONE.id()), SNP.id(), "1");
        List<ImportedGig> listed = List.of(new ImportedGig(Platform.of(BANDZONE.id()), renamed, "1"),
                new ImportedGig(Platform.of(BANDZONE.id()), renamed, "2"));

        assertThat(Reconciler.drifts(TestPlatforms.PLATFORMS.traits(BANDZONE), List.of(SNP), List.of(stored), listed,
                Set.of())).singleElement().extracting(Drift::kind).isEqualTo(Drift.Kind.DIFFERENT);
        assertThat(Reconciler.unlinked(BANDZONE, List.of(stored), listed)).isEqualTo(1);
    }

    @Test
    void a_copy_in_a_same_named_town_of_another_country_is_found() {
        Gig czechKosice = gig(SNP.title(), SNP.schedule().start(), SNP.location().venue(), "Košice", Country.CZECHIA);

        assertThat(check(BANDZONE, SNP, czechKosice)).singleElement().satisfies(drift -> {
            assertThat(drift.kind()).isEqualTo(Drift.Kind.DIFFERENT);
            assertThat(drift.differences()).singleElement().asString()
                    .contains("country: catalog Slovakia, Bandzone Czechia", "same name elsewhere");
        });
    }

    @Test
    void another_town_another_day_or_time_or_name_is_found() {
        Gig moved = gig("SNP", at(8, 30, 20, 0), "Secret Garden", "Bratislava", Country.SLOVAKIA);
        Gig laterSameDay = gig(SNP.title(), at(8, 29, 22, 30), SNP.location().venue(), "Košice", Country.SLOVAKIA);

        assertThat(check(BANDSINTOWN, SNP, moved)).singleElement().extracting(Drift::differences).asList()
                .anySatisfy(d -> assertThat(d.toString()).startsWith("date: catalog 29 Aug 2026, Bandsintown 30 Aug 2026"))
                .anySatisfy(d -> assertThat(d.toString()).startsWith("name: catalog"))
                .anySatisfy(d -> assertThat(d.toString()).isEqualTo("town: catalog Košice, Bandsintown Bratislava"));
        assertThat(check(BANDSINTOWN, SNP, laterSameDay)).singleElement().extracting(Drift::differences).asList()
                .map(Object::toString).containsExactly("time: catalog 21:00, Bandsintown 22:30");
    }

    @Test
    void a_copy_placed_in_a_same_named_town_far_away_is_found_by_its_coordinates() {
        Gig hranice = Gig.create("Eufory + Snaefell", GigSchedule.startingAt(at(9, 18, 21, 30)),
                new Location("Zámecký klub", "Hranice", Country.CZECHIA,
                        new Address(null, "753 01", "okres Přerov", "Olomoucký kraj", 49.548, 17.735)),
                List.of(), Admission.free(), null, null, null, null);
        List<Publication> records = List.of(new Publication(BANDSINTOWN, hranice.id(), "1"));

        assertThat(Reconciler.drifts(TestPlatforms.BANDSINTOWN_TRAITS, List.of(hranice), records,
                List.of(new ImportedGig(BANDSINTOWN, hranice, "1", 50.205, 12.208)), Set.of()))   // Hranice, okres Cheb
                .singleElement().extracting(Drift::differences).asList().singleElement().asString()
                .startsWith("place: Bandsintown put it ").endsWith("km from Hranice (okres Přerov)");
        assertThat(Reconciler.drifts(TestPlatforms.BANDSINTOWN_TRAITS, List.of(hranice), records,
                List.of(new ImportedGig(BANDSINTOWN, hranice, "1", 49.5476, 17.7347)), Set.of())).isEmpty();
    }

    @Test
    void what_the_platforms_are_expected_to_show_differently_is_not_a_drift() {
        Gig googleNamed = gig("snp - spolu na pódiu košice", SNP.schedule().start(),
                "Secret Garden (Skrytý Dvor) - Piváreň - Koncerty", "Košice I", Country.SLOVAKIA);
        Gig festival = Gig.create("Motosraz", new GigSchedule(at(8, 27, 0, 0), at(8, 30, 0, 0),
                        new Slot(at(8, 28, 22, 0), at(8, 28, 23, 30))),
                new Location("Auto Camping Dubník", "Stará Turá", Country.SLOVAKIA), List.of(), Admission.free(),
                null, null, null, null);
        Gig slotOnBandsintown = gig("Motosraz", at(8, 28, 22, 0), "Auto Camping Dubník", "Stará Turá", Country.SLOVAKIA);

        Gig noVenue = gig(SNP.title(), SNP.schedule().start(), null, "Košice", Country.SLOVAKIA);
        Gig dashVenue = gig(SNP.title(), SNP.schedule().start(), "-", "Košice", Country.SLOVAKIA);

        assertThat(check(BANDSINTOWN, SNP, googleNamed)).as("district, longer venue name, case").isEmpty();
        assertThat(check(BANDZONE, noVenue, dashVenue)).as("a placeholder is no venue").isEmpty();
        assertThat(check(BANDSINTOWN, festival, slotOnBandsintown)).as("Bandsintown shows the band's slot").isEmpty();
        assertThat(check(BANDZONE, festival, slotOnBandsintown)).as("Bandzone shows the whole event").isNotEmpty();
    }

    @Test
    void a_gone_event_is_missing_unless_bandsintown_removed_it_for_a_cancelled_gig() {
        assertThat(check(BANDZONE, SNP, null)).singleElement().extracting(Drift::kind).isEqualTo(Drift.Kind.MISSING);
        assertThat(check(BANDSINTOWN, SNP.cancel(), null)).as("cancelling removes it there").isEmpty();
        assertThat(check(BANDZONE, SNP.cancel(), SNP)).singleElement().extracting(Drift::differences).asList()
                .map(Object::toString).containsExactly("cancelled in the catalog, not on Bandzone");
    }

    @Test
    void gigs_being_synced_are_skipped_and_events_no_gig_is_published_as_are_counted() {
        Gig other = gig("Other", at(9, 1, 20, 0), "Klub", "Martin", Country.SLOVAKIA);
        List<Publication> records = List.of(new Publication(BANDZONE, SNP.id(), "1"));
        List<ImportedGig> listed = List.of(new ImportedGig(BANDZONE, other, "1"), new ImportedGig(BANDZONE, other, "2"));

        assertThat(Reconciler.drifts(TestPlatforms.BANDZONE_TRAITS, List.of(SNP), records, listed, Set.of(SNP.id()))).isEmpty();
        assertThat(Reconciler.unlinked(BANDZONE, records, listed)).isEqualTo(1);
    }
}
