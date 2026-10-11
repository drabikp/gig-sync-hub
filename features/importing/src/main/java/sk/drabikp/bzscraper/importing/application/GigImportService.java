package sk.drabikp.bzscraper.importing.application;

import sk.drabikp.bzscraper.catalog.application.port.in.GigWrites;
import sk.drabikp.bzscraper.gig.application.port.out.GigRepository;
import sk.drabikp.bzscraper.gig.application.port.out.PublishedGigStore;
import sk.drabikp.bzscraper.gig.application.port.out.Transactions;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.gig.domain.platform.Platforms;
import sk.drabikp.bzscraper.importing.application.port.in.ImportGigsUseCase;
import sk.drabikp.bzscraper.importing.application.port.out.GigImporter;
import sk.drabikp.bzscraper.importing.application.port.out.PlatformReads;
import sk.drabikp.bzscraper.importing.domain.GigMerge;
import sk.drabikp.bzscraper.importing.domain.ImportDecision;
import sk.drabikp.bzscraper.importing.domain.ImportPlan;
import sk.drabikp.bzscraper.importing.domain.ImportPlanner;
import sk.drabikp.bzscraper.importing.domain.ImportProposal;
import sk.drabikp.bzscraper.importing.domain.ImportResult;
import sk.drabikp.bzscraper.importing.domain.ImportedGig;
import sk.drabikp.bzscraper.sync.application.port.in.QueueSyncWork;
import sk.drabikp.bzscraper.sync.application.port.out.PlatformException;
import sk.drabikp.bzscraper.sync.domain.QueueResult;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Import orchestrator: reads the chosen platforms through their {@link GigImporter}s,
 * plans with {@link ImportPlanner}, and applies the user's decisions — saving the merged
 * gig ({@link GigMerge}) and recording each platform copy's id against it — in one
 * transaction. A new importable platform is one more {@code GigImporter} bean.
 */
public class GigImportService implements ImportGigsUseCase {

    private final Map<Platform, GigImporter> importers = new TreeMap<>();
    private final GigRepository gigRepository;
    private final PublishedGigStore publishedGigStore;
    private final Transactions transactions;
    private final Platforms platforms;
    private final GigWrites writes;
    private final QueueSyncWork sync;

    public GigImportService(List<GigImporter> importers, GigRepository gigRepository,
                            PublishedGigStore publishedGigStore, Transactions transactions, Platforms platforms,
                            GigWrites writes, QueueSyncWork sync) {
        for (GigImporter importer : importers) {
            if (this.importers.put(importer.platform(), importer) != null) {
                throw new IllegalStateException("Two GigImporters registered for platform " + importer.platform());
            }
        }
        this.gigRepository = gigRepository;
        this.publishedGigStore = publishedGigStore;
        this.transactions = transactions;
        this.platforms = platforms;
        this.writes = writes;
        this.sync = sync;
    }

    @Override
    public Set<Platform> importablePlatforms() {
        return new LinkedHashSet<>(platforms.ordered(importers.keySet()));
    }

    @Override
    public ImportPlan plan(BandId band, Set<Platform> from) {
        List<ImportedGig> imported = new ArrayList<>();
        Map<Platform, String> failures = new TreeMap<>();
        for (Platform platform : from) {
            GigImporter importer = importers.get(platform);
            if (importer == null) {
                failures.put(platform, "Importing from this platform is not supported.");
                continue;
            }
            try {
                imported.addAll(importer.importGigs(band));
            } catch (PlatformException e) {
                failures.put(platform, e.getMessage());
            } catch (RuntimeException e) {
                failures.put(platform, PlatformReads.unexpected(platform, e));
            }
        }
        return ImportPlanner.plan(gigRepository.findAll(band), publishedGigStore.all(band), imported, failures,
                platforms);
    }

    @Override
    public ImportResult apply(BandId band, List<ImportDecision> decisions) {
        Tally tally = new Tally();
        List<QueueResult> queued = new ArrayList<>();
        transactions.inTransaction(() -> {
            for (ImportDecision decision : decisions) {
                if (!decision.include()) {
                    continue;
                }
                ImportProposal proposal = decision.proposal();
                if (proposal.suggested() && !decision.sameGig()) {
                    proposal.copies().forEach(copy -> importOnItsOwn(band, copy, tally));
                } else {
                    queued.add(importTogether(band, proposal, decision.chosen(), tally));
                }
            }
        });
        queued.forEach(result -> sync.signal(band, result));
        return tally.result();
    }

    /**
     * The chosen version, filled in from the others, becomes the catalog gig — through the
     * catalog's own write path: a new gig must not overwrite another; an existing one is
     * replaced only as it was when the plan was read, and its platforms (those it was on
     * before this import) get the update. Then the imported copies are linked to it.
     */
    private QueueResult importTogether(BandId band, ImportProposal proposal, ImportProposal.Version chosen,
                                       Tally tally) {
        Gig local = proposal.local();
        Gig merged = GigMerge.merge(local, chosen, proposal.versions(), platforms);
        QueueResult queued = QueueResult.NOTHING;
        if (local == null) {
            writes.add(band, merged);
            tally.added++;
        } else if (!merged.equals(local)) {
            queued = writes.replace(band, local, merged);
            tally.updated++;
        }
        for (ImportedGig copy : proposal.copies()) {
            writes.link(band, copy.platform(), merged.id(), copy.externalRef());
            tally.linked++;
        }
        return queued;
    }

    /** A suggested match the user rejected: the copy becomes its own catalog gig. */
    private void importOnItsOwn(BandId band, ImportedGig copy, Tally tally) {
        if (gigRepository.findByMatch(band, copy.gig().match()).isPresent()) {
            tally.skipped++;                     // that day and venue are another catalog gig's
            return;
        }
        writes.add(band, copy.gig());
        writes.link(band, copy.platform(), copy.gig().id(), copy.externalRef());
        tally.added++;
        tally.linked++;
    }

    /** What the import did, counted as it goes. */
    private static final class Tally {

        private int added;
        private int updated;
        private int linked;
        private int skipped;

        ImportResult result() {
            return new ImportResult(added, updated, linked, skipped);
        }
    }
}
