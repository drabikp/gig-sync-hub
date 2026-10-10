package sk.drabikp.bzscraper.importing.application;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.catalog.application.CatalogFakes;
import sk.drabikp.bzscraper.catalog.application.port.in.GigIdentityTakenException;
import sk.drabikp.bzscraper.gig.application.ConcurrentChangeException;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.TestGigs;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.gig.domain.platform.TestPlatforms;
import sk.drabikp.bzscraper.importing.application.port.out.GigImporter;
import sk.drabikp.bzscraper.importing.domain.ImportDecision;
import sk.drabikp.bzscraper.importing.domain.ImportPlan;
import sk.drabikp.bzscraper.importing.domain.ImportProposal;
import sk.drabikp.bzscraper.importing.domain.ImportResult;
import sk.drabikp.bzscraper.importing.domain.ImportedGig;
import sk.drabikp.bzscraper.sync.application.SyncFakes;
import sk.drabikp.bzscraper.sync.application.SyncRequests;
import sk.drabikp.bzscraper.sync.application.port.out.FailureKind;
import sk.drabikp.bzscraper.sync.application.port.out.PlatformException;
import sk.drabikp.bzscraper.sync.domain.SyncAction;
import sk.drabikp.bzscraper.sync.domain.SyncTask;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static sk.drabikp.bzscraper.gig.domain.TestBands.BAND;
import static sk.drabikp.bzscraper.gig.domain.TestBands.OTHER;
import static sk.drabikp.bzscraper.gig.domain.platform.TestPlatforms.BANDSINTOWN;
import static sk.drabikp.bzscraper.gig.domain.platform.TestPlatforms.BANDZONE;

class GigImportServiceTest {

    private final SyncFakes.Gigs gigs = new SyncFakes.Gigs();
    private final SyncFakes.Published published = new SyncFakes.Published();
    private final SyncFakes.Outbox outbox = new SyncFakes.Outbox();
    private final SyncFakes.Signals signals = new SyncFakes.Signals();
    private final SyncFakes.MutableClock clock = new SyncFakes.MutableClock();
    private final SyncRequests requests = SyncFakes.requests(outbox, published, signals, clock);

    private final Gig klub = TestGigs.gig("Fest", "Klub 007");
    private final Gig klubRenamed = TestGigs.gig("Fest 2026", "Klub 007");

    private static GigImporter importer(Platform platform, Listing listed) {
        return new GigImporter() {
            @Override
            public Platform platform() {
                return platform;
            }

            @Override
            public List<ImportedGig> importGigs(BandId band) throws PlatformException {
                return listed.get(band);
            }
        };
    }

    /** What an importer reads, or how it fails. */
    @FunctionalInterface
    private interface Listing {
        List<ImportedGig> get(BandId band) throws PlatformException;
    }

    /** The defaults: include it, accept non-suggested grouping, use the default version. */
    private static ImportDecision byDefault(ImportProposal proposal) {
        return new ImportDecision(proposal, true, !proposal.suggested(), proposal.defaultVersion());
    }

    private GigImportService service(GigImporter... importers) {
        return new GigImportService(List.of(importers), gigs, published, new SyncFakes.DirectTransactions(),
                TestPlatforms.PLATFORMS, CatalogFakes.writes(gigs, published, outbox, requests), requests);
    }

    private GigImportService service() {
        return service(importer(BANDZONE, band -> List.of()), importer(BANDSINTOWN, band -> List.of()));
    }

    @Test
    void importable_platforms_are_the_registered_importers_in_the_platforms_order() {
        assertThat(service(importer(BANDSINTOWN, band -> List.of()), importer(BANDZONE, band -> List.of())).importablePlatforms())
                .containsExactly(BANDZONE, BANDSINTOWN);
    }

    @Test
    void a_platform_that_cannot_be_read_is_reported_and_the_others_are_still_planned() {
        GigImportService service = service(importer(BANDZONE, band -> List.of(new ImportedGig(BANDZONE, klub, "100"))),
                importer(BANDSINTOWN, band -> {
                    throw new PlatformException("authenticator code rejected", null, FailureKind.NEEDS_USER);
                }));

        ImportPlan plan = service.plan(BAND, Set.of(BANDZONE, BANDSINTOWN));

        assertThat(plan.proposals()).hasSize(1);
        assertThat(plan.failures()).containsEntry(BANDSINTOWN, "authenticator code rejected");
    }

    @Test
    void an_unexpected_error_while_reading_is_reported_short_not_as_its_raw_message() {
        GigImportService service = service(importer(BANDZONE, band -> {
            throw new NullPointerException("Cannot invoke \"org.jsoup.nodes.Element.select\" because ...");
        }));

        assertThat(service.plan(BAND, Set.of(BANDZONE)).failures().get(BANDZONE))
                .startsWith("an unexpected error (NullPointerException)").doesNotContain("jsoup");
    }

    @Test
    void a_new_gig_found_on_both_platforms_is_saved_once_and_linked_to_both() {
        ImportProposal proposal = new ImportProposal(null, List.of(
                new ImportedGig(BANDZONE, klub, "100"), new ImportedGig(BANDSINTOWN, klub, "900")), false);

        ImportResult result = service().apply(BAND, List.of(byDefault(proposal)));

        assertThat(gigs.findAll(BAND)).containsExactly(klub);
        assertThat(published.externalRef(BAND, BANDZONE, klub.id())).contains("100");
        assertThat(published.externalRef(BAND, BANDSINTOWN, klub.id())).contains("900");
        assertThat(result).isEqualTo(new ImportResult(1, 0, 2, 0));
        assertThat(outbox.all()).as("nothing to send: the platforms are where it came from").isEmpty();
    }

    @Test
    void choosing_a_platform_version_updates_the_catalog_gig_and_the_platforms_it_was_already_on() {
        gigs.save(BAND, klub);
        published.record(BAND, BANDSINTOWN, klub.id(), "900");              // on Bandsintown before this import
        ImportProposal proposal = new ImportProposal(klub, List.of(new ImportedGig(BANDZONE, klubRenamed, "100")),
                false);

        ImportResult result = service().apply(BAND, List.of(new ImportDecision(proposal, true, true,
                proposal.versions().get(1))));

        assertThat(gigs.findById(BAND, klub.id())).contains(klubRenamed);
        assertThat(published.externalRef(BAND, BANDZONE, klubRenamed.id())).contains("100");
        assertThat(result).isEqualTo(new ImportResult(0, 1, 1, 0));
        assertThat(outbox.all()).extracting(SyncTask::platform, SyncTask::action)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(BANDSINTOWN, SyncAction.UPDATE));
    }

    @Test
    void keeping_the_catalog_version_only_links_the_platform_event() {
        gigs.save(BAND, klub);
        ImportProposal proposal = new ImportProposal(klub, List.of(new ImportedGig(BANDZONE, klubRenamed, "100")),
                false);

        ImportResult result = service().apply(BAND, List.of(byDefault(proposal)));

        assertThat(gigs.findById(BAND, klub.id())).contains(klub);
        assertThat(published.externalRef(BAND, BANDZONE, klub.id())).contains("100");
        assertThat(result).isEqualTo(new ImportResult(0, 0, 1, 0));
    }

    @Test
    void choosing_a_version_with_another_venue_moves_the_catalog_gig_and_its_links() {
        gigs.save(BAND, klub);
        published.record(BAND, BANDZONE, klub.id(), "100");
        Gig elsewhere = TestGigs.gig("Fest", "Lucerna");
        ImportProposal proposal = new ImportProposal(klub, List.of(new ImportedGig(BANDSINTOWN, elsewhere, "900")),
                true);

        service().apply(BAND, List.of(new ImportDecision(proposal, true, true, proposal.versions().get(1))));

        assertThat(gigs.findById(BAND, klub.id())).isEmpty();
        assertThat(gigs.findById(BAND, elsewhere.id())).isPresent();
        assertThat(published.externalRef(BAND, BANDZONE, elsewhere.id())).contains("100");
        assertThat(published.externalRef(BAND, BANDSINTOWN, elsewhere.id())).contains("900");
    }

    @Test
    void the_catalog_rules_hold_for_import_too_nothing_is_written_when_one_says_no() {
        Gig changedSincePlan = TestGigs.gig("Fest — renamed meanwhile", "Klub 007");
        gigs.save(BAND, changedSincePlan);
        ImportProposal stale = new ImportProposal(klub, List.of(new ImportedGig(BANDZONE, klubRenamed, "100")),
                false);
        Gig other = TestGigs.gig("Other", "Lucerna");
        gigs.save(BAND, other);
        ImportProposal ontoOther = new ImportProposal(null, List.of(new ImportedGig(BANDZONE, other, "200")), false);

        assertThatThrownBy(() -> service().apply(BAND, List.of(new ImportDecision(stale, true, true,
                stale.versions().get(1))))).isInstanceOf(ConcurrentChangeException.class);
        assertThatThrownBy(() -> service().apply(BAND, List.of(byDefault(ontoOther))))
                .isInstanceOf(GigIdentityTakenException.class);
        assertThat(published.all(BAND)).isEmpty();
    }

    @Test
    void a_rejected_suggestion_imports_each_copy_on_its_own() {
        Gig bz = TestGigs.gig("Fest", "Lucerna Music Bar");
        Gig bit = TestGigs.gig("Fest", "Lucerna");
        ImportProposal proposal = new ImportProposal(null, List.of(
                new ImportedGig(BANDZONE, bz, "100"), new ImportedGig(BANDSINTOWN, bit, "900")), true);

        ImportResult result = service().apply(BAND, List.of(byDefault(proposal)));   // suggestion: not confirmed

        assertThat(gigs.findAll(BAND)).containsExactlyInAnyOrder(bz, bit);
        assertThat(published.externalRef(BAND, BANDZONE, bz.id())).contains("100");
        assertThat(published.externalRef(BAND, BANDSINTOWN, bit.id())).contains("900");
        assertThat(result.added()).isEqualTo(2);
    }

    @Test
    void unticked_proposals_are_not_imported() {
        ImportProposal proposal = new ImportProposal(null, List.of(new ImportedGig(BANDZONE, klub, "100")), false);

        service().apply(BAND, List.of(new ImportDecision(proposal, false, true, proposal.defaultVersion())));

        assertThat(gigs.findAll(BAND)).isEmpty();
        assertThat(published.all(BAND)).isEmpty();
    }

    @Test
    void a_band_imports_its_own_gigs_and_its_plan_knows_only_its_own_catalog() {
        gigs.save(OTHER, klub);                                        // another band plays there too
        published.record(OTHER, BANDZONE, klub.id(), "100");
        GigImportService service = service(importer(BANDZONE, band -> band.equals(BAND)
                ? List.of(new ImportedGig(BANDZONE, klub, "200")) : List.of()));

        ImportPlan plan = service.plan(BAND, Set.of(BANDZONE));

        assertThat(plan.proposals()).singleElement().satisfies(p -> assertThat(p.local()).isNull());
        service.apply(BAND, plan.proposals().stream().map(GigImportServiceTest::byDefault).toList());
        assertThat(published.externalRef(BAND, BANDZONE, klub.id())).contains("200");
        assertThat(published.externalRef(OTHER, BANDZONE, klub.id())).as("the other band's link").contains("100");
    }

    @Test
    void two_importers_for_the_same_platform_is_rejected() {
        assertThatThrownBy(() -> service(importer(BANDZONE, band -> List.of()), importer(BANDZONE, band -> List.of())))
                .isInstanceOf(IllegalStateException.class);
    }
}
