package sk.drabikp.gigsynchub.importing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import sk.drabikp.gigsynchub.catalog.application.port.in.GigWrites;
import sk.drabikp.gigsynchub.gig.application.port.out.GigRepository;
import sk.drabikp.gigsynchub.gig.application.port.out.LiveUpdates;
import sk.drabikp.gigsynchub.gig.application.port.out.PublishedGigStore;
import sk.drabikp.gigsynchub.gig.application.port.out.RowScope;
import sk.drabikp.gigsynchub.gig.application.port.out.Transactions;
import sk.drabikp.gigsynchub.gig.domain.platform.Platforms;
import sk.drabikp.gigsynchub.importing.application.GigImportService;
import sk.drabikp.gigsynchub.importing.application.ImportSession;
import sk.drabikp.gigsynchub.importing.application.port.out.GigImporter;
import sk.drabikp.gigsynchub.sync.application.port.in.QueueSyncWork;

import java.util.List;

@Configuration
class ImportingConfiguration {

    @Bean
    GigImportService gigImportService(List<GigImporter> importers, GigRepository gigRepository,
                                      PublishedGigStore publishedGigStore, Transactions transactions,
                                      Platforms platforms, GigWrites catalogWrites, QueueSyncWork queueSyncWork) {
        return new GigImportService(importers, gigRepository, publishedGigStore, transactions, platforms,
                catalogWrites, queueSyncWork);
    }

    /** The one import in progress; the platforms are read on Spring's task executor. */
    @Bean
    ImportSession importSession(GigImportService gigImportService, TaskExecutor executor, LiveUpdates live,
                                RowScope rows) {
        return new ImportSession(gigImportService, executor, live, rows);
    }
}
