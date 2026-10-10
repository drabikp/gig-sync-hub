package sk.drabikp.bzscraper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import sk.drabikp.bzscraper.catalog.application.port.in.DeleteGigUseCase;
import sk.drabikp.bzscraper.catalog.application.port.in.SaveGigUseCase;
import sk.drabikp.bzscraper.gig.application.port.out.PublishedGigStore;
import sk.drabikp.bzscraper.gig.domain.Address;
import sk.drabikp.bzscraper.gig.domain.Admission;
import sk.drabikp.bzscraper.gig.domain.Country;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.GigSchedule;
import sk.drabikp.bzscraper.gig.domain.Location;
import sk.drabikp.bzscraper.gig.domain.platform.TestPlatforms;
import sk.drabikp.bzscraper.sync.application.port.in.DispatchSyncUseCase;
import sk.drabikp.bzscraper.sync.application.port.in.PublishGigsUseCase;
import sk.drabikp.bzscraper.sync.application.port.in.SyncLogUseCase;
import sk.drabikp.bzscraper.sync.domain.SyncStatus;
import sk.drabikp.bzscraper.sync.domain.SyncTask;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static sk.drabikp.bzscraper.gig.domain.TestBands.BAND;

/**
 * Live, end to end through the workflow engine: publishes a made-up gig to the Bandzone TEST
 * band (create form step), then deletes it (remove step). Everything else is the real app
 * on an in-memory database; Bandsintown stays switched off. Skipped unless
 * {@code BZ_LIVE_WORKFLOW=true}; reads BZ_LOGIN / BZ_PASSWORD / BZ_SLUG — the test band only.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "BZ_LIVE_WORKFLOW", matches = "true")
class BandzoneWorkflowLiveTest {

    @DynamicPropertySource
    static void testBand(DynamicPropertyRegistry registry) {
        registry.add("bzscraper.bandzone.client", () -> "http");
        registry.add("bzscraper.bandzone.login", () -> System.getenv("BZ_LOGIN"));
        registry.add("bzscraper.bandzone.password", () -> System.getenv("BZ_PASSWORD"));
        registry.add("bzscraper.bandzone.band-slug", () -> System.getenv("BZ_SLUG"));
    }

    @Autowired
    private SaveGigUseCase saveGig;
    @Autowired
    private PublishGigsUseCase publishGigs;
    @Autowired
    private DeleteGigUseCase deleteGig;
    @Autowired
    private DispatchSyncUseCase dispatcher;
    @Autowired
    private SyncLogUseCase syncLog;
    @Autowired
    private PublishedGigStore publishedGigStore;

    @Test
    void publishes_to_the_test_band_and_removes_it_again() {
        Gig gig = Gig.create("TEST workflow engine (smazat)",
                GigSchedule.startingAt(ZonedDateTime.of(2027, 3, 12, 20, 0, 0, 0, ZoneId.of("Europe/Prague"))),
                new Location("Zámecký klub", "Hranice", Country.CZECHIA,
                        new Address(null, "753 01", "okres Přerov", "Olomoucký kraj", 49.548, 17.735)),
                List.of(), Admission.free(), "Automated test — will be deleted.", null, null, null);
        saveGig.add(BAND, gig);

        publishGigs.publish(BAND, Set.of(TestPlatforms.BANDZONE), List.of(gig));
        runAll();
        String bandzoneId = publishedGigStore.externalRef(BAND, TestPlatforms.BANDZONE, gig.id()).orElse(null);
        System.out.println("Workflow live test: created Bandzone concert " + bandzoneId + " — "
                + syncLog.history(BAND, gig.id(), 5).stream().map(t -> t.action() + " " + t.status() + " " + t.message()).toList());

        deleteGig.delete(BAND, gig.id());
        runAll();

        assertThat(bandzoneId).as("published through the create form").isNotNull();
        assertThat(publishedGigStore.isPublished(BAND, TestPlatforms.BANDZONE, gig.id())).as("removed and forgotten").isFalse();
        assertThat(syncLog.history(BAND, gig.id(), 2)).extracting(SyncTask::status).containsOnly(SyncStatus.DONE);
    }

    private void runAll() {
        while (dispatcher.runNext()) {
            // run until nothing is due
        }
    }
}
