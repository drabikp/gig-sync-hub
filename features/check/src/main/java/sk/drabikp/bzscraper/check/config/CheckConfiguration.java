package sk.drabikp.bzscraper.check.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import sk.drabikp.bzscraper.catalog.application.port.in.GigWrites;
import sk.drabikp.bzscraper.check.application.BackgroundCheck;
import sk.drabikp.bzscraper.check.application.PlatformCheckService;
import sk.drabikp.bzscraper.gig.application.port.out.GigRepository;
import sk.drabikp.bzscraper.gig.application.port.out.LiveUpdates;
import sk.drabikp.bzscraper.gig.application.port.out.PublishedGigStore;
import sk.drabikp.bzscraper.gig.application.port.out.Transactions;
import sk.drabikp.bzscraper.gig.domain.platform.Platforms;
import sk.drabikp.bzscraper.importing.application.port.out.GigImporter;
import sk.drabikp.bzscraper.sync.application.port.in.SyncStateUseCase;

import java.time.Clock;
import java.util.List;

@Configuration
class CheckConfiguration {

    /** Reads the platforms and compares them with the catalog (reconciliation; read-only). */
    @Bean
    PlatformCheckService platformCheckService(List<GigImporter> importers, GigRepository gigRepository,
                                              PublishedGigStore publishedGigStore, GigWrites catalogWrites,
                                              SyncStateUseCase syncState, Transactions transactions,
                                              Platforms platforms, Clock clock, CheckProperties check,
                                              LiveUpdates live) {
        return new PlatformCheckService(importers, gigRepository, publishedGigStore, catalogWrites, syncState,
                transactions, platforms, clock, check.pastDays(), live);
    }

    /** A check the user starts runs on Spring's task executor. */
    @Bean
    BackgroundCheck backgroundCheck(PlatformCheckService platformCheckService, TaskExecutor executor) {
        return new BackgroundCheck(platformCheckService, executor);
    }
}
