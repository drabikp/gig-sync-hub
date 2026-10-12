package sk.drabikp.gigsynchub.importing.application.port.in;

import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.gig.domain.platform.Platform;
import sk.drabikp.gigsynchub.importing.domain.ImportDecision;
import sk.drabikp.gigsynchub.importing.domain.ImportPlan;
import sk.drabikp.gigsynchub.importing.domain.ImportResult;

import java.util.List;
import java.util.Set;

/**
 * Brings the band's existing gigs from the platforms into the catalog, linked to their
 * platform events — so they are managed from here (edit, cancel, re-sync) instead of
 * being published again. Two steps: {@link #plan} reads the platforms and proposes what
 * to add or link; {@link #apply} carries out the user's decisions. Import never deletes.
 */
public interface ImportGigsUseCase {

    Set<Platform> importablePlatforms();

    /** Reads the given platforms; a platform that can't be read is reported, not fatal. */
    ImportPlan plan(BandId band, Set<Platform> platforms);

    ImportResult apply(BandId band, List<ImportDecision> decisions);
}
