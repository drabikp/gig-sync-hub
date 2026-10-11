package sk.drabikp.bzscraper.check.application;

import sk.drabikp.bzscraper.catalog.application.port.in.GigWrites;
import sk.drabikp.bzscraper.check.application.port.in.CheckPlatformsUseCase;
import sk.drabikp.bzscraper.check.application.port.out.CheckResultStore;
import sk.drabikp.bzscraper.check.domain.Drift;
import sk.drabikp.bzscraper.check.domain.PlatformCheck;
import sk.drabikp.bzscraper.check.domain.Reconciler;
import sk.drabikp.bzscraper.gig.application.port.out.GigRepository;
import sk.drabikp.bzscraper.gig.application.port.out.LiveUpdates;
import sk.drabikp.bzscraper.gig.application.port.out.PublishedGigStore;
import sk.drabikp.bzscraper.gig.application.port.out.Transactions;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.DateRange;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.gig.domain.platform.Platforms;
import sk.drabikp.bzscraper.gig.domain.platform.Publication;
import sk.drabikp.bzscraper.importing.application.port.out.GigImporter;
import sk.drabikp.bzscraper.importing.application.port.out.PlatformReads;
import sk.drabikp.bzscraper.importing.domain.ImportedGig;
import sk.drabikp.bzscraper.sync.application.port.in.SyncStateUseCase;
import sk.drabikp.bzscraper.sync.application.port.out.PlatformException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Checks the platforms against the catalog ({@link Reconciler}) for the upcoming gigs and
 * those of the last {@code pastDays}, reading each platform through its importer — the same
 * read-only reading as Import. A platform held back by a circuit breaker (the band's or the platform's)
 * is not read; one that can't be read is reported as such. One check at a time per band; each band's
 * latest result is kept ({@link CheckResultStore} — a restart doesn't forget it).
 */
public class PlatformCheckService implements CheckPlatformsUseCase {

    private final List<GigImporter> importers;
    private final GigRepository gigRepository;
    private final PublishedGigStore publishedGigStore;
    private final GigWrites writes;
    private final SyncStateUseCase sync;
    private final Transactions transactions;
    private final Platforms platforms;
    private final Clock clock;
    private final LiveUpdates live;
    private final int pastDays;
    private final Set<BandId> running = ConcurrentHashMap.newKeySet();
    private final CheckResultStore results;

    public PlatformCheckService(List<GigImporter> importers, GigRepository gigRepository,
                                PublishedGigStore publishedGigStore, GigWrites writes, SyncStateUseCase sync,
                                Transactions transactions, Platforms platforms, Clock clock, int pastDays,
                                LiveUpdates live, CheckResultStore results) {
        this.importers = importers;
        this.gigRepository = gigRepository;
        this.publishedGigStore = publishedGigStore;
        this.writes = writes;
        this.sync = sync;
        this.transactions = transactions;
        this.platforms = platforms;
        this.clock = clock;
        this.live = live;
        this.pastDays = pastDays;
        this.results = results;
    }

    @Override
    public Optional<PlatformCheck> check(BandId band) {
        if (!running.add(band)) {
            return Optional.empty();
        }
        live.changed(band, LiveUpdates.Topic.CHECK);
        try {
            List<Drift> drifts = new ArrayList<>();
            Map<Platform, String> unreadable = new TreeMap<>();
            Map<Platform, Integer> unlinked = new TreeMap<>();
            for (GigImporter importer : importers) {
                Platform platform = importer.platform();
                if (sync.heldBack(band, platform)) {
                    unreadable.put(platform, "held back after failing again and again — not read this time");
                    continue;
                }
                List<ImportedGig> listed;
                try {
                    listed = importer.importGigs(band);
                } catch (PlatformException e) {
                    unreadable.put(platform, e.getMessage());
                    continue;
                } catch (RuntimeException e) {
                    unreadable.put(platform, PlatformReads.unexpected(platform, e));
                    continue;
                }
                // the catalog as it is once the platform has been read (that read takes a while):
                // upcoming gigs and the recent past — older history isn't worth reconciling
                LocalDate since = LocalDate.now(clock).minusDays(pastDays);
                List<Gig> catalog = gigRepository.findStartingWithin(band, DateRange.from(since));
                List<Publication> records = publishedGigStore.all(band);
                Set<GigId> syncing = sync.gigsWithOpenWork(band);
                drifts.addAll(Reconciler.drifts(platforms.traits(platform), catalog, records, listed, syncing));
                unlinked.put(platform, Reconciler.unlinked(platform, records, listed));
            }
            PlatformCheck check = new PlatformCheck(clock.instant(), drifts, unreadable, unlinked);
            results.save(band, check);
            return Optional.of(check);
        } finally {
            running.remove(band);
            live.changed(band, LiveUpdates.Topic.CHECK);
        }
    }

    @Override
    public Optional<PlatformCheck> lastCheck(BandId band) {
        return results.of(band);
    }

    @Override
    public boolean running(BandId band) {
        return running.contains(band);
    }

    /** Through the catalog's write path (refused while the gig's platform work runs); the gig pages follow. */
    @Override
    public void forget(BandId band, Platform platform, GigId gigId) {
        transactions.inTransaction(() -> writes.forgetLink(band, platform, gigId));
        live.changed(band, LiveUpdates.Topic.GIGS);
        dismiss(band, platform, gigId);
    }

    /** A check that ends meanwhile replaces the result; a dismiss never brings an older one back. */
    @Override
    public void dismiss(BandId band, Platform platform, GigId gigId) {
        results.of(band).ifPresent(check -> results.save(band, check.without(platform, gigId)));
        live.changed(band, LiveUpdates.Topic.CHECK);
    }
}
