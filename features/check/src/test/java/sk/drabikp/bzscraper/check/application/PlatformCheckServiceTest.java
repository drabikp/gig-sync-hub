package sk.drabikp.bzscraper.check.application;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.catalog.application.CatalogFakes;
import sk.drabikp.bzscraper.catalog.application.port.in.GigWrites;
import sk.drabikp.bzscraper.check.application.port.out.CheckResultStore;
import sk.drabikp.bzscraper.check.domain.Drift;
import sk.drabikp.bzscraper.check.domain.PlatformCheck;
import sk.drabikp.bzscraper.gig.application.port.out.LiveUpdates;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.TestGigs;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.gig.domain.platform.TestPlatforms;
import sk.drabikp.bzscraper.importing.application.port.out.GigImporter;
import sk.drabikp.bzscraper.importing.domain.ImportedGig;
import sk.drabikp.bzscraper.sync.application.SyncFakes;
import sk.drabikp.bzscraper.sync.application.engine.PlatformBreakers;
import sk.drabikp.bzscraper.sync.application.port.out.FailureKind;
import sk.drabikp.bzscraper.sync.application.port.out.PlatformException;
import sk.drabikp.bzscraper.sync.domain.Lane;
import sk.drabikp.bzscraper.sync.domain.SyncAction;
import sk.drabikp.bzscraper.sync.domain.SyncTask;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static sk.drabikp.bzscraper.gig.domain.TestBands.BAND;
import static sk.drabikp.bzscraper.gig.domain.TestBands.OTHER;
import static sk.drabikp.bzscraper.gig.domain.platform.TestPlatforms.BANDSINTOWN;
import static sk.drabikp.bzscraper.gig.domain.platform.TestPlatforms.BANDZONE;

class PlatformCheckServiceTest {

    private final SyncFakes.Gigs gigs = new SyncFakes.Gigs();
    private final SyncFakes.Published published = new SyncFakes.Published();
    private final SyncFakes.Outbox outbox = new SyncFakes.Outbox();
    private final SyncFakes.Signals signals = new SyncFakes.Signals();
    private final SyncFakes.MutableClock clock = new SyncFakes.MutableClock();
    private final PlatformBreakers breakers = new PlatformBreakers(1, 3, Duration.ofMinutes(30), clock, signals,
            signals);
    /** Each band's latest check, as a store keeps them. */
    private final Map<BandId, PlatformCheck> kept = new HashMap<>();
    private final CheckResultStore results = new CheckResultStore() {
        @Override
        public Optional<PlatformCheck> of(BandId band) {
            return Optional.ofNullable(kept.get(band));
        }

        @Override
        public void save(BandId band, PlatformCheck check) {
            kept.put(band, check);
        }
    };

    private final Gig fest = TestGigs.gig("Fest", "Klub 007");
    private final Gig renamedThere = TestGigs.gig("Fest — changed by hand", "Klub 007");

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

    private PlatformCheckService service(GigImporter... importers) {
        return new PlatformCheckService(List.of(importers), gigs, published, writes(),
                SyncFakes.state(outbox, breakers), new SyncFakes.DirectTransactions(), TestPlatforms.PLATFORMS, clock,
                3650, LiveUpdates.NONE, results);
    }

    private GigWrites writes() {
        return CatalogFakes.writes(gigs, published, outbox, SyncFakes.requests(outbox, published, new SyncFakes.Signals(),
                clock));
    }

    @Test
    void reads_every_platform_and_reports_what_differs_and_what_couldnt_be_read() {
        gigs.save(BAND, fest);
        published.record(BAND, BANDZONE, fest.id(), "563380");
        published.record(BAND, BANDSINTOWN, fest.id(), "109010841");
        PlatformCheckService service = service(
                importer(BANDZONE, band -> List.of(new ImportedGig(BANDZONE, renamedThere, "563380"),
                        new ImportedGig(BANDZONE, renamedThere, "999"))),
                importer(BANDSINTOWN, band -> {
                    throw new PlatformException("Bandsintown login failed", null, FailureKind.TEMPORARY);
                }));

        PlatformCheck check = service.check(BAND).orElseThrow();

        assertThat(check.drifts()).singleElement().satisfies(drift -> {
            assertThat(drift.platform()).isEqualTo(BANDZONE);
            assertThat(drift.kind()).isEqualTo(Drift.Kind.DIFFERENT);
            assertThat(drift.differences()).singleElement().asString().startsWith("name:");
        });
        assertThat(check.unreadable()).containsEntry(BANDSINTOWN, "Bandsintown login failed");
        assertThat(check.unlinked()).containsEntry(BANDZONE, 1);
        assertThat(service.lastCheck(BAND)).contains(check);
        assertThat(service.running(BAND)).isFalse();
        assertThat(service(importer(BANDZONE, band -> List.of())).lastCheck(BAND)).as("kept across a restart")
                .contains(check);
    }

    @Test
    void a_platform_held_back_by_its_breaker_is_not_read_and_gigs_being_synced_are_skipped() {
        gigs.save(BAND, fest);
        published.record(BAND, BANDZONE, fest.id(), "563380");
        outbox.enqueue(BAND, fest.id(), SyncTask.labelOf(fest), BANDZONE, SyncAction.UPDATE, clock.instant());
        breakers.failed(new Lane(BAND, BANDSINTOWN), "timeout");
        int[] bandsintownReads = {0};
        PlatformCheckService service = service(
                importer(BANDZONE, band -> List.of(new ImportedGig(BANDZONE, renamedThere, "563380"))),
                importer(BANDSINTOWN, band -> {
                    bandsintownReads[0]++;
                    return List.of();
                }));

        PlatformCheck check = service.check(BAND).orElseThrow();

        assertThat(check.drifts()).as("its update is still queued").isEmpty();
        assertThat(check.unreadable()).containsKey(BANDSINTOWN);
        assertThat(bandsintownReads[0]).isZero();
    }

    @Test
    void history_older_than_the_window_is_not_reconciled() {
        gigs.save(BAND, fest);                                             // 2026-09-15, 23 days before "now"
        published.record(BAND, BANDZONE, fest.id(), "563380");
        PlatformCheckService lastWeekOnly = new PlatformCheckService(
                List.of(importer(BANDZONE, band -> List.of(new ImportedGig(BANDZONE, renamedThere, "563380")))),
                gigs, published, writes(), SyncFakes.state(outbox, breakers), new SyncFakes.DirectTransactions(),
                TestPlatforms.PLATFORMS, clock, 7, LiveUpdates.NONE, results);

        assertThat(lastWeekOnly.check(BAND).orElseThrow().drifts()).isEmpty();
    }

    @Test
    void forgetting_a_gone_event_drops_the_link_so_publish_creates_it_again() {
        gigs.save(BAND, fest);
        published.record(BAND, BANDZONE, fest.id(), "563380");
        PlatformCheckService service = service(importer(BANDZONE, band -> List.of()));
        assertThat(service.check(BAND).orElseThrow().drifts()).singleElement()
                .extracting(Drift::kind).isEqualTo(Drift.Kind.MISSING);

        service.forget(BAND, BANDZONE, fest.id());

        assertThat(published.isPublished(BAND, BANDZONE, fest.id())).isFalse();
        assertThat(service.lastCheck(BAND).orElseThrow().drifts()).isEmpty();
    }

    @Test
    void each_band_is_checked_against_its_own_catalog_and_keeps_its_own_result() {
        gigs.save(OTHER, fest);                                      // only the other band has the gig
        published.record(OTHER, BANDZONE, fest.id(), "563380");
        PlatformCheckService service = service(importer(BANDZONE, band -> band.equals(OTHER)
                ? List.of(new ImportedGig(BANDZONE, renamedThere, "563380")) : List.of()));

        assertThat(service.check(BAND).orElseThrow().drifts()).isEmpty();
        assertThat(service.check(OTHER).orElseThrow().drifts()).singleElement()
                .extracting(Drift::kind).isEqualTo(Drift.Kind.DIFFERENT);

        service.dismiss(BAND, BANDZONE, fest.id());
        assertThat(service.lastCheck(OTHER).orElseThrow().drifts()).as("another band's dismiss").hasSize(1);
        service.forget(OTHER, BANDZONE, fest.id());
        assertThat(published.isPublished(OTHER, BANDZONE, fest.id())).isFalse();
        assertThat(service.lastCheck(BAND).orElseThrow().drifts()).isEmpty();
    }
}
