package sk.drabikp.bzscraper.catalog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import sk.drabikp.bzscraper.catalog.application.CatalogWrites;
import sk.drabikp.bzscraper.catalog.application.GigCatalogService;
import sk.drabikp.bzscraper.catalog.application.GigExportService;
import sk.drabikp.bzscraper.catalog.application.port.out.GigExporter;
import sk.drabikp.bzscraper.gig.application.port.out.GigRepository;
import sk.drabikp.bzscraper.gig.application.port.out.LiveUpdates;
import sk.drabikp.bzscraper.gig.application.port.out.PublishedGigStore;
import sk.drabikp.bzscraper.gig.application.port.out.Transactions;
import sk.drabikp.bzscraper.sync.application.port.in.QueueSyncWork;
import sk.drabikp.bzscraper.sync.application.port.in.SyncStateUseCase;

import java.time.Clock;
import java.util.List;

@Configuration
class CatalogConfiguration {

    /** The one write path for gigs — its rules hold for every writer. */
    @Bean
    CatalogWrites catalogWrites(GigRepository gigRepository, PublishedGigStore publishedGigStore,
                                QueueSyncWork queueSyncWork, SyncStateUseCase syncState) {
        return new CatalogWrites(gigRepository, publishedGigStore, queueSyncWork, syncState);
    }

    @Bean
    GigCatalogService gigCatalogService(GigRepository gigRepository, PublishedGigStore publishedGigStore,
                                        Transactions transactions, QueueSyncWork queueSyncWork,
                                        CatalogWrites catalogWrites, LiveUpdates live, Clock clock) {
        return new GigCatalogService(gigRepository, publishedGigStore, transactions, queueSyncWork, catalogWrites,
                live, clock);
    }

    @Bean
    GigExportService gigExportService(GigRepository gigRepository, List<GigExporter> exporters) {
        return new GigExportService(gigRepository, exporters);
    }
}
