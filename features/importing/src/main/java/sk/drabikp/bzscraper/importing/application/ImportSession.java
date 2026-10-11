package sk.drabikp.bzscraper.importing.application;

import org.jmolecules.architecture.hexagonal.Application;
import sk.drabikp.bzscraper.gig.application.BadRequestException;
import sk.drabikp.bzscraper.gig.application.UserFacingException;
import sk.drabikp.bzscraper.gig.application.port.out.LiveUpdates;
import sk.drabikp.bzscraper.gig.application.port.out.RowScope;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.importing.application.port.in.ImportErrorCode;
import sk.drabikp.bzscraper.importing.application.port.in.ImportGigsUseCase;
import sk.drabikp.bzscraper.importing.application.port.in.ImportSessionUseCase;
import sk.drabikp.bzscraper.importing.domain.ImportDecision;
import sk.drabikp.bzscraper.importing.domain.ImportPlan;
import sk.drabikp.bzscraper.importing.domain.ImportProposal;
import sk.drabikp.bzscraper.importing.domain.ImportResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Each band's one import in progress ({@link ImportSessionUseCase}) over {@link ImportGigsUseCase}: the
 * read runs on the {@code executor}, its start and end are announced ({@link LiveUpdates.Topic#IMPORT}),
 * and so is the import itself (and the catalog it changed).
 */
@Application
public class ImportSession implements ImportSessionUseCase {

    private static final System.Logger log = System.getLogger(ImportSession.class.getName());

    private final ImportGigsUseCase importGigs;
    private final Executor executor;
    private final LiveUpdates live;
    private final RowScope rows;
    /** Where the import stands, as one value: whoever reads it never sees half of a change. */
    private final Map<BandId, AtomicReference<Progress>> progress = new ConcurrentHashMap<>();

    /** A read under way (keeping the last plan until it ends), the last read's failure, the plan to review. */
    private record Progress(boolean reading, String readError, ImportPlan plan) {

        static final Progress IDLE = new Progress(false, null, null);

        Progress startReading() {
            return new Progress(true, null, plan);
        }

        Progress read(ImportPlan newPlan) {
            return new Progress(false, null, newPlan);
        }

        Progress failed(String error) {
            return new Progress(false, error, plan);
        }

        Progress applied() {
            return new Progress(false, null, null);
        }
    }

    public ImportSession(ImportGigsUseCase importGigs, Executor executor, LiveUpdates live) {
        this(importGigs, executor, live, RowScope.NONE);
    }

    /** {@code rows}: the background read sees the band's rows only. */
    public ImportSession(ImportGigsUseCase importGigs, Executor executor, LiveUpdates live, RowScope rows) {
        this.importGigs = importGigs;
        this.executor = executor;
        this.live = live;
        this.rows = rows;
    }

    /** The band's import (each band has its own, one at a time). */
    private AtomicReference<Progress> progress(BandId band) {
        return progress.computeIfAbsent(band, b -> new AtomicReference<>(Progress.IDLE));
    }

    @Override
    public State state(BandId band) {
        Progress now = progress(band).get();
        return new State(importGigs.importablePlatforms(), now.reading(), now.readError(), now.plan());
    }

    @Override
    public synchronized void read(BandId band, Collection<Platform> platforms) {
        AtomicReference<Progress> progress = progress(band);
        if (progress.get().reading()) {
            return;
        }
        Set<Platform> from = new LinkedHashSet<>(platforms);
        if (from.isEmpty()) {
            throw new UserFacingException(ImportErrorCode.NO_PLATFORM, Map.of(), "Pick at least one platform.");
        }
        progress.updateAndGet(Progress::startReading);
        live.changed(band, LiveUpdates.Topic.IMPORT);
        executor.execute(() -> rows.as(band, () -> {
            try {
                ImportPlan plan = importGigs.plan(band, from);
                progress.updateAndGet(p -> p.read(plan));
            } catch (RuntimeException e) {
                log.log(System.Logger.Level.ERROR, "Reading the platforms for an import failed", e);
                String error = e instanceof UserFacingException ? e.getMessage()
                        : "something went wrong — details are in the log";
                progress.updateAndGet(p -> p.failed(error));
            } finally {
                live.changed(band, LiveUpdates.Topic.IMPORT);
            }
        }));
    }

    /** Refused while the platforms are read: the plan the user decided on is about to be replaced. */
    @Override
    public synchronized ImportResult apply(BandId band, List<Choice> choices) {
        AtomicReference<Progress> progress = progress(band);
        Progress now = progress.get();
        if (now.reading()) {
            throw new UserFacingException(ImportErrorCode.IMPORT_READING, Map.of(),
                    "The platforms are being read again — decide on the new plan when it is there.");
        }
        ImportPlan current = now.plan();
        if (current == null) {
            throw new UserFacingException(ImportErrorCode.NO_PLAN, Map.of(), "Read the platforms first.");
        }
        List<ImportDecision> decisions = new ArrayList<>();
        for (Choice choice : choices) {
            decisions.add(decision(current.proposals(), choice));
        }
        if (decisions.stream().noneMatch(ImportDecision::include)) {
            throw new UserFacingException(ImportErrorCode.NOTHING_SELECTED, Map.of(), "Nothing selected to import.");
        }
        ImportResult result = importGigs.apply(band, decisions);
        progress.updateAndGet(Progress::applied);
        live.changed(band, LiveUpdates.Topic.IMPORT);
        live.changed(band, LiveUpdates.Topic.GIGS);
        return result;
    }

    /** The choice about a proposal; a version that isn't there keeps the proposal's default. */
    private static ImportDecision decision(List<ImportProposal> proposals, Choice choice) {
        if (choice.index() < 0 || choice.index() >= proposals.size()) {
            throw new BadRequestException("no proposal " + choice.index());
        }
        ImportProposal proposal = proposals.get(choice.index());
        List<ImportProposal.Version> versions = proposal.versions();
        ImportProposal.Version chosen = choice.version() >= 0 && choice.version() < versions.size()
                ? versions.get(choice.version()) : proposal.defaultVersion();
        return new ImportDecision(proposal, choice.include(), choice.sameGig(), chosen);
    }
}
