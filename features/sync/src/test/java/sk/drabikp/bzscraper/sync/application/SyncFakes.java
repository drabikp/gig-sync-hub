package sk.drabikp.bzscraper.sync.application;

import sk.drabikp.bzscraper.gig.application.port.out.GigRepository;
import sk.drabikp.bzscraper.gig.application.port.out.PublishedGigStore;
import sk.drabikp.bzscraper.gig.application.port.out.Transactions;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.DateRange;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.gig.domain.platform.Publication;
import sk.drabikp.bzscraper.gig.domain.platform.TestPlatforms;
import sk.drabikp.bzscraper.sync.application.engine.FairTurns;
import sk.drabikp.bzscraper.sync.application.engine.OutcomeSettlement;
import sk.drabikp.bzscraper.sync.application.engine.PlatformBreakers;
import sk.drabikp.bzscraper.sync.application.engine.PlatformHealth;
import sk.drabikp.bzscraper.sync.application.engine.RecreateStep;
import sk.drabikp.bzscraper.sync.application.engine.RunPreparation;
import sk.drabikp.bzscraper.sync.application.engine.StepRegistry;
import sk.drabikp.bzscraper.sync.application.engine.SyncAdmission;
import sk.drabikp.bzscraper.sync.application.engine.SyncDispatcher;
import sk.drabikp.bzscraper.sync.application.engine.WorkflowEngine;
import sk.drabikp.bzscraper.sync.application.port.in.PauseSyncUseCase;
import sk.drabikp.bzscraper.sync.application.port.out.SettingsStore;
import sk.drabikp.bzscraper.sync.application.port.out.SyncFaults;
import sk.drabikp.bzscraper.sync.application.port.out.SyncNotifier;
import sk.drabikp.bzscraper.sync.application.port.out.SyncOutbox;
import sk.drabikp.bzscraper.sync.application.port.out.SyncStep;
import sk.drabikp.bzscraper.sync.application.port.out.SyncTrigger;
import sk.drabikp.bzscraper.sync.domain.StepOutcome;
import sk.drabikp.bzscraper.sync.domain.StepType;
import sk.drabikp.bzscraper.sync.domain.SyncAction;
import sk.drabikp.bzscraper.sync.domain.SyncLogEntry;
import sk.drabikp.bzscraper.sync.domain.SyncStatus;
import sk.drabikp.bzscraper.sync.domain.SyncTask;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/** In-memory stand-ins for the sync ports, behaving like the JPA adapters. */
public final class SyncFakes {

    private SyncFakes() {
    }

    public static final class Outbox implements SyncOutbox {

        private final Map<Long, SyncTask> tasks = new LinkedHashMap<>();
        private final List<SyncLogEntry> log = new ArrayList<>();
        private long nextId = 1;

        public synchronized List<SyncTask> all() {
            return List.copyOf(tasks.values());
        }

        public synchronized SyncTask get(long id) {
            return tasks.get(id);
        }

        /**
         * Keeps the platform as its id, like the database: what comes back is an equal platform, not the
         * caller's instance — so a comparison with {@code ==} fails here as it does in the app.
         */
        @Override
        public synchronized SyncTask enqueue(BandId band, GigId gigId, String gigLabel, Platform platform, SyncAction action,
                                Instant now) {
            SyncTask task = new SyncTask(nextId++, band, gigId, gigLabel, Platform.of(platform.id()), action,
                    SyncStatus.PENDING, 0, now, now, now, null, null);
            tasks.put(task.id(), task);
            write(task.id(), now, "queued");
            return task;
        }

        @Override
        public synchronized Optional<SyncTask> find(long id) {
            return Optional.ofNullable(tasks.get(id));
        }

        @Override
        public synchronized List<SyncTask> openFor(BandId band, GigId gigId) {
            return tasks.values().stream().filter(t -> t.band().equals(band) && t.gigId().equals(gigId)
                    && t.status().open()).toList();
        }

        @Override
        public synchronized List<SyncTask> unfinished(BandId band) {
            return tasks.values().stream().filter(t -> t.band().equals(band)
                    && (t.status().open() || t.status() == SyncStatus.FAILED)).toList();
        }

        @Override
        public synchronized List<SyncTask> recent(BandId band, int limit) {
            return tasks.values().stream().filter(t -> t.band().equals(band))
                    .sorted(Comparator.comparingLong(SyncTask::id).reversed())
                    .limit(limit).toList();
        }

        @Override
        public synchronized List<SyncTask> history(BandId band, GigId gigId, int limit) {
            return tasks.values().stream().filter(t -> t.band().equals(band) && t.gigId().equals(gigId))
                    .sorted(Comparator.comparingLong(SyncTask::id).reversed()).limit(limit).toList();
        }

        @Override
        public synchronized List<SyncLogEntry> log(long taskId) {
            return log.stream().filter(e -> e.taskId() == taskId).toList();
        }

        @Override
        public synchronized List<SyncTask> supersede(BandId band, GigId gigId, Platform platform, String why, Instant now) {
            List<SyncTask> pending = tasks.values().stream().filter(t -> t.band().equals(band) && t.gigId().equals(gigId)
                    && t.platform().equals(platform) && t.status() == SyncStatus.PENDING).toList();
            pending.forEach(t -> change(t.id(), SyncStatus.DISCARDED, t.attempts(), null, "superseded: " + why, now));
            return pending;
        }

        @Override
        public synchronized List<SyncTask> replaceFailed(BandId band, GigId gigId, Platform platform, Set<SyncAction> actions,
                                            String why, Instant now) {
            List<SyncTask> failed = tasks.values().stream().filter(t -> t.band().equals(band) && t.gigId().equals(gigId)
                    && t.platform().equals(platform) && t.status() == SyncStatus.FAILED && actions.contains(t.action()))
                    .toList();
            failed.forEach(t -> change(t.id(), SyncStatus.DISCARDED, t.attempts(), null, "replaced: " + why, now));
            return failed;
        }

        @Override
        public synchronized void move(BandId band, GigId from, GigId to, String newLabel) {
            tasks.replaceAll((id, t) -> t.band().equals(band) && t.gigId().equals(from)
                    ? new SyncTask(t.id(), t.band(), to, newLabel, t.platform(),
                    t.action(), t.status(), t.attempts(), t.createdAt(), t.nextAttemptAt(), t.updatedAt(),
                    t.message(), t.step()) : t);
        }

        @Override
        public synchronized Optional<SyncTask> nextDue(Instant now) {
            return tasks.values().stream().filter(t -> due(t, now)).findFirst();
        }

        @Override
        public synchronized List<SyncTask> due(Instant now) {
            return tasks.values().stream().filter(t -> due(t, now)).toList();
        }

        @Override
        public synchronized List<SyncTask> due(BandId band, Platform platform, SyncAction action, Instant now) {
            return tasks.values().stream().filter(t -> due(t, now) && t.band().equals(band)
                    && t.platform().equals(platform) && t.action() == action).toList();
        }

        @Override
        public synchronized List<SyncTask> running() {
            return tasks.values().stream().filter(t -> t.status() == SyncStatus.RUNNING).toList();
        }

        @Override
        public synchronized boolean markRunning(long id, Instant now) {
            SyncTask t = tasks.get(id);
            if (t.status() != SyncStatus.PENDING) {
                return false;
            }
            change(id, SyncStatus.RUNNING, t.attempts() + 1, null, t.message(), now);
            return true;
        }

        @Override
        public synchronized void markDone(long id, String message, Instant now) {
            change(id, SyncStatus.DONE, tasks.get(id).attempts(), null, message, now);
        }

        @Override
        public synchronized void markRetry(long id, String error, Instant nextAttempt, Instant now) {
            change(id, SyncStatus.PENDING, tasks.get(id).attempts(), nextAttempt, error, now);
        }

        @Override
        public synchronized void postpone(long id, String why, Instant nextAttempt, Instant now) {
            SyncTask t = tasks.get(id);
            if (t.status() == SyncStatus.RUNNING) {
                change(id, SyncStatus.PENDING, Math.max(0, t.attempts() - 1), nextAttempt, why, now);
            }
        }

        @Override
        public synchronized void markFailed(long id, String error, Instant now) {
            change(id, SyncStatus.FAILED, tasks.get(id).attempts(), null, error, now);
        }

        @Override
        public synchronized void advance(long id, StepType next, String why, Instant now) {
            SyncTask t = tasks.get(id);
            if (!t.status().canBecome(SyncStatus.PENDING)) {
                return;                                 // as the real outbox: a finished task stays so
            }
            tasks.put(id, new SyncTask(id, t.band(), t.gigId(), t.gigLabel(), t.platform(), t.action(), SyncStatus.PENDING, 0,
                    t.createdAt(), now, now, t.message(), next));
            write(id, now, why + " → " + next.label());
        }

        @Override
        public synchronized void requeue(long id, Instant now) {
            change(id, SyncStatus.PENDING, 0, now, null, now);
        }

        @Override
        public synchronized void discard(long id, Instant now) {
            change(id, SyncStatus.DISCARDED, tasks.get(id).attempts(), null, "discarded by you", now);
        }

        private static boolean due(SyncTask t, Instant now) {
            return t.status() == SyncStatus.PENDING && !t.nextAttemptAt().isAfter(now);
        }

        private void change(long id, SyncStatus status, int attempts, Instant next, String message, Instant now) {
            SyncTask t = tasks.get(id);
            if (!t.status().canBecome(status)) {
                return;                                 // as the real outbox: a late or racing write is ignored
            }
            tasks.put(id, new SyncTask(id, t.band(), t.gigId(), t.gigLabel(), t.platform(), t.action(), status, attempts,
                    t.createdAt(), next, now, message, t.step()));
            write(id, now, status + (message == null ? "" : ": " + message));
        }

        private void write(long id, Instant at, String message) {
            log.add(new SyncLogEntry(id, at, message));
        }
    }

    public static final class Published implements PublishedGigStore {

        private final Map<String, Publication> records = new LinkedHashMap<>();
        private final Map<String, BandId> bands = new HashMap<>();

        private static String key(BandId band, Platform platform, GigId id) {
            return band + "|" + platform + "|" + id.key();
        }

        @Override
        public synchronized boolean isPublished(BandId band, Platform platform, GigId gigId) {
            return records.containsKey(key(band, platform, gigId));
        }

        @Override
        public synchronized Optional<String> externalRef(BandId band, Platform platform, GigId gigId) {
            return Optional.ofNullable(records.get(key(band, platform, gigId))).map(Publication::externalRef)
                    .filter(ref -> !ref.isBlank());
        }

        @Override
        public synchronized void record(BandId band, Platform platform, GigId gigId, String externalRef) {
            records.put(key(band, platform, gigId), new Publication(Platform.of(platform.id()), gigId, externalRef));
            bands.put(key(band, platform, gigId), band);
        }

        @Override
        public synchronized void remove(BandId band, Platform platform, GigId gigId) {
            records.remove(key(band, platform, gigId));
            bands.remove(key(band, platform, gigId));
        }

        @Override
        public synchronized void move(BandId band, GigId from, GigId to) {
            for (Publication p : all(band)) {
                if (p.gigId().equals(from)) {
                    remove(band, p.platform(), from);
                    record(band, p.platform(), to, p.externalRef());
                }
            }
        }

        @Override
        public synchronized List<Publication> all(BandId band) {
            return records.entrySet().stream().filter(e -> band.equals(bands.get(e.getKey())))
                    .map(Map.Entry::getValue).toList();
        }
    }

    public static final class Gigs implements GigRepository {

        private final Map<BandId, Map<GigId, Gig>> gigs = new HashMap<>();

        private Map<GigId, Gig> of(BandId band) {
            return gigs.computeIfAbsent(band, b -> new LinkedHashMap<>());
        }

        @Override
        public synchronized void save(BandId band, Gig gig) {
            of(band).put(gig.id(), gig);
        }

        @Override
        public synchronized Optional<Gig> findById(BandId band, GigId id) {
            return Optional.ofNullable(of(band).get(id));
        }

        @Override
        public synchronized List<Gig> findAll(BandId band) {
            return List.copyOf(of(band).values());
        }

        @Override
        public synchronized List<Gig> findStartingWithin(BandId band, DateRange range) {
            return findAll(band);
        }

        @Override
        public synchronized void deleteById(BandId band, GigId id) {
            of(band).remove(id);
        }
    }

    /** Runs the work directly; counts how often a transaction was used. */
    public static final class DirectTransactions implements Transactions {

        public int used;

        @Override
        public void inTransaction(Runnable work) {
            used++;
            work.run();
        }

        @Override
        public <T> T computeInTransaction(Supplier<T> work) {
            used++;
            return work.get();
        }
    }

    public static final class Signals implements SyncTrigger, SyncNotifier {

        public int wakes;
        public int changes;

        @Override
        public synchronized void wake() {
            wakes++;
        }

        @Override
        public synchronized void changed(BandId band) {
            changes++;
        }

        @Override
        public synchronized void changedEverywhere() {
            changes++;
        }
    }

    /** Settings in memory. */
    public static final class Settings implements SettingsStore {

        public final Map<BandId, Map<String, String>> values = new HashMap<>();

        @Override
        public synchronized Optional<String> get(BandId band, String name) {
            return Optional.ofNullable(values.getOrDefault(band, Map.of()).get(name));
        }

        @Override
        public synchronized void put(BandId band, String name, String value) {
            Map<String, String> of = values.computeIfAbsent(band, b -> new HashMap<>());
            if (value == null) {
                of.remove(name);
            } else {
                of.put(name, value);
            }
        }

        @Override
        public synchronized Map<BandId, String> all(String name) {
            Map<BandId, String> all = new HashMap<>();
            values.forEach((band, of) -> {
                if (of.containsKey(name)) {
                    all.put(band, of.get(name));
                }
            });
            return all;
        }
    }

    /** A clock the test moves forward. */
    public static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-10-08T10:00:00Z");

        public void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    /** A workflow step that records what it was given; refusal and outcome are set per test. */
    public static final class Step implements SyncStep {

        private final Platform platform;
        private final StepType type;
        private final int batchSize;
        public Function<Gig, Optional<String>> refusal = gig -> Optional.empty();
        public Function<Item, StepOutcome> outcome = item -> StepOutcome.done(null);
        public final List<List<Item>> calls = new ArrayList<>();

        public Step(Platform platform, StepType type, int batchSize) {
            this.platform = platform;
            this.type = type;
            this.batchSize = batchSize;
        }

        @Override
        public synchronized Platform platform() {
            return platform;
        }

        @Override
        public synchronized StepType type() {
            return type;
        }

        @Override
        public synchronized int batchSize() {
            return batchSize;
        }

        @Override
        public synchronized Optional<String> refusal(Gig gig) {
            return refusal.apply(gig);
        }

        @Override
        public synchronized List<StepOutcome> run(List<Item> items) {
            calls.add(List.copyOf(items));
            return items.stream().map(outcome).toList();
        }

        public synchronized List<Item> ran() {
            return calls.stream().flatMap(List::stream).toList();
        }
    }

    // --- the sync services wired with these fakes and TestPlatforms ---

    public static StepRegistry registry(Published published, SyncStep... steps) {
        return new StepRegistry(List.of(steps), TestPlatforms.PLATFORMS,
                RecreateStep.recreator(new DirectTransactions(), published));
    }

    /** What the engine reported as unexpected failures of steps. */
    public static final class Faults implements SyncFaults {

        public final List<RuntimeException> reported = new ArrayList<>();

        @Override
        public void unexpected(SyncStep step, RuntimeException failure) {
            reported.add(failure);
        }
    }

    public static WorkflowEngine engine(StepRegistry steps, Gigs gigs, Published published, Outbox outbox, Signals signals,
                                 Clock clock, PauseSyncUseCase pause, PlatformHealth health) {
        return engine(steps, gigs, published, outbox, signals, clock, pause, health, new Faults());
    }

    public static WorkflowEngine engine(StepRegistry steps, Gigs gigs, Published published, Outbox outbox, Signals signals,
                                 Clock clock, PauseSyncUseCase pause, PlatformHealth health, SyncFaults faults) {
        RunPreparation preparation = new RunPreparation(steps, gigs, published, outbox, TestPlatforms.PLATFORMS, clock);
        OutcomeSettlement settlement = new OutcomeSettlement(steps, published, outbox, new DirectTransactions(),
                TestPlatforms.PLATFORMS, clock);
        return new WorkflowEngine(steps, preparation, settlement, outbox, signals, faults, clock, pause, health);
    }

    /** Never paused, platforms never held back. */
    public static WorkflowEngine engine(StepRegistry steps, Gigs gigs, Published published, Outbox outbox, Signals signals,
                                 Clock clock) {
        return engine(steps, gigs, published, outbox, signals, clock, new SyncPause(signals, signals, new Settings()),
                PlatformBreakers.never(clock));
    }

    public static SyncDispatcher dispatcher(WorkflowEngine engine, Outbox outbox, Signals signals, Clock clock,
                                     PauseSyncUseCase pause, PlatformHealth health) {
        return new SyncDispatcher(engine, outbox, signals, clock, pause, health, TestPlatforms.PLATFORMS,
                new FairTurns());
    }

    public static SyncDispatcher dispatcher(WorkflowEngine engine, Outbox outbox, Signals signals, Clock clock) {
        return dispatcher(engine, outbox, signals, clock, new SyncPause(signals, signals, new Settings()), PlatformBreakers.never(clock));
    }

    /** Every platform admits all work (tests that don't care which steps the platforms have). */
    public static SyncState state(Outbox outbox) {
        return state(outbox, PlatformBreakers.never(Clock.systemUTC()));
    }

    public static SyncState state(Outbox outbox, PlatformHealth health) {
        return new SyncState(outbox, health);
    }

    public static SyncRequests requests(Outbox outbox, Published published, Signals signals, Clock clock) {
        return requests(outbox, published, signals, clock, SyncAdmission.ALL);
    }

    public static SyncRequests requests(Outbox outbox, Published published, Signals signals, Clock clock,
                                 SyncAdmission admission) {
        return new SyncRequests(outbox, published, signals, signals, clock, admission, TestPlatforms.PLATFORMS);
    }
}
