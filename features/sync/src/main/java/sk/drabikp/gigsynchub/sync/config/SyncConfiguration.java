package sk.drabikp.gigsynchub.sync.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import sk.drabikp.gigsynchub.gig.application.port.out.GigRepository;
import sk.drabikp.gigsynchub.gig.application.port.out.LiveUpdates;
import sk.drabikp.gigsynchub.gig.application.port.out.PublishedGigStore;
import sk.drabikp.gigsynchub.gig.application.port.out.RowScope;
import sk.drabikp.gigsynchub.gig.application.port.out.Transactions;
import sk.drabikp.gigsynchub.gig.domain.platform.Platforms;
import sk.drabikp.gigsynchub.sync.application.GigPublishingService;
import sk.drabikp.gigsynchub.sync.application.GigResyncService;
import sk.drabikp.gigsynchub.sync.application.SyncChanges;
import sk.drabikp.gigsynchub.sync.application.SyncLogService;
import sk.drabikp.gigsynchub.sync.application.SyncPause;
import sk.drabikp.gigsynchub.sync.application.SyncRequests;
import sk.drabikp.gigsynchub.sync.application.SyncState;
import sk.drabikp.gigsynchub.sync.application.SyncWakeUp;
import sk.drabikp.gigsynchub.sync.application.engine.FairTurns;
import sk.drabikp.gigsynchub.sync.application.engine.OutcomeSettlement;
import sk.drabikp.gigsynchub.sync.application.engine.PlatformBreakers;
import sk.drabikp.gigsynchub.sync.application.engine.RecreateStep;
import sk.drabikp.gigsynchub.sync.application.engine.RunIntake;
import sk.drabikp.gigsynchub.sync.application.engine.StepRegistry;
import sk.drabikp.gigsynchub.sync.application.engine.SyncDispatcher;
import sk.drabikp.gigsynchub.sync.application.engine.WorkflowEngine;
import sk.drabikp.gigsynchub.sync.application.port.out.SettingsStore;
import sk.drabikp.gigsynchub.sync.application.port.out.SyncFaults;
import sk.drabikp.gigsynchub.sync.application.port.out.SyncNotifier;
import sk.drabikp.gigsynchub.sync.application.port.out.SyncOutbox;
import sk.drabikp.gigsynchub.sync.application.port.out.SyncStep;
import sk.drabikp.gigsynchub.sync.application.port.out.SyncTrigger;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/** The sync's services: queueing, the engine over the platforms' steps, breakers, pause, the log. */
@Configuration
class SyncConfiguration {

    /** Tells the pages following the sync that it changed. */
    @Bean
    SyncChanges syncChanges(LiveUpdates live) {
        return new SyncChanges(live);
    }

    /** Rung when platform work is queued; the sync worker listens. */
    @Bean
    SyncWakeUp syncWakeUp() {
        return new SyncWakeUp();
    }

    /** The platforms' sync steps — what each can do. */
    @Bean
    StepRegistry stepRegistry(List<SyncStep> steps, Platforms platforms, Transactions transactions,
                              PublishedGigStore publishedGigStore) {
        return new StepRegistry(steps, platforms, RecreateStep.recreator(transactions, publishedGigStore));
    }

    /** Runs a band's due work on a platform through its workflow — docs/sync-workflow-plan.md. */
    @Bean
    WorkflowEngine workflowEngine(StepRegistry stepRegistry, GigRepository gigRepository,
                                  PublishedGigStore publishedGigStore, SyncOutbox outbox, Transactions transactions,
                                  SyncNotifier notifier, SyncFaults faults, Clock clock, Platforms platforms,
                                  SyncPause syncPause, PlatformBreakers platformBreakers) {
        RunIntake intake = new RunIntake(gigRepository, publishedGigStore, platforms);
        OutcomeSettlement settlement = new OutcomeSettlement(publishedGigStore, outbox, transactions, platforms, clock);
        return new WorkflowEngine(stepRegistry, intake, settlement, outbox, notifier, faults, clock, syncPause,
                platformBreakers, platforms);
    }

    /**
     * A band's work on a platform failing this many batches in a row is held back this long (then one trial
     * batch); so many bands held back on one platform hold it back for all.
     */
    @Bean
    PlatformBreakers platformBreakers(Clock clock, SyncNotifier notifier, SyncTrigger trigger, SyncProperties sync) {
        return new PlatformBreakers(sync.breaker().failures(), sync.breaker().platformBands(),
                Duration.ofMinutes(sync.breaker().cooldownMinutes()), clock, notifier, trigger);
    }

    /** Whose work runs next — the bands in turn, one workload per lane at a time. */
    @Bean
    FairTurns fairTurns() {
        return new FairTurns();
    }

    @Bean
    SyncRequests syncRequests(SyncOutbox outbox, PublishedGigStore publishedGigStore, SyncTrigger trigger,
                              SyncNotifier notifier, Clock clock, StepRegistry stepRegistry, Platforms platforms) {
        return new SyncRequests(outbox, publishedGigStore, trigger, notifier, clock, stepRegistry, platforms);
    }

    @Bean
    SyncState syncState(SyncOutbox outbox, PlatformBreakers platformBreakers) {
        return new SyncState(outbox, platformBreakers);
    }

    @Bean
    GigPublishingService gigPublishingService(SyncRequests syncRequests, Transactions transactions,
                                              Platforms platforms) {
        return new GigPublishingService(syncRequests, transactions, platforms);
    }

    @Bean
    GigResyncService gigResyncService(GigRepository gigRepository, SyncRequests syncRequests, SyncState syncState,
                                      Transactions transactions) {
        return new GigResyncService(gigRepository, syncRequests, syncState, transactions);
    }

    @Bean
    SyncPause syncPause(SyncTrigger trigger, SyncNotifier notifier, SettingsStore settings) {
        return new SyncPause(trigger, notifier, settings);
    }

    @Bean
    SyncDispatcher syncDispatcher(WorkflowEngine workflowEngine, SyncOutbox outbox, SyncNotifier notifier,
                                  Clock clock, SyncPause syncPause, PlatformBreakers platformBreakers,
                                  Platforms platforms, FairTurns fairTurns, RowScope rowScope) {
        return new SyncDispatcher(workflowEngine, outbox, notifier, clock, syncPause, platformBreakers, platforms,
                fairTurns, rowScope);
    }

    @Bean
    SyncLogService syncLogService(SyncOutbox outbox, SyncTrigger trigger, SyncNotifier notifier, Clock clock) {
        return new SyncLogService(outbox, trigger, notifier, clock);
    }
}
