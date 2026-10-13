package sk.drabikp.gigsynchub.importing.domain;

import org.jmolecules.ddd.annotation.Service;
import sk.drabikp.gigsynchub.gig.domain.CityName;
import sk.drabikp.gigsynchub.gig.domain.Gig;
import sk.drabikp.gigsynchub.gig.domain.GigId;
import sk.drabikp.gigsynchub.gig.domain.GigMatch;
import sk.drabikp.gigsynchub.gig.domain.platform.Platform;
import sk.drabikp.gigsynchub.gig.domain.platform.Platforms;
import sk.drabikp.gigsynchub.gig.domain.platform.Publication;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure domain service: plans an import from one or more platforms into the catalog.
 * <ol>
 *   <li>Platform gigs already linked to the catalog (their platform id is recorded) are
 *       left alone.</li>
 *   <li>The rest are grouped by what concert they are ({@link GigMatch}: date + venue) — with
 *       the catalog gig of that day and venue, and with the same gig from the other platform.</li>
 *   <li>A gig still on its own is <em>suggested</em> as the same concert as a catalog gig,
 *       or as a gig from the other platform, on the same date in the same city — but
 *       only when exactly one such candidate exists. The user confirms suggestions.</li>
 * </ol>
 * A platform gig whose day and venue belong to a catalog gig already linked on that platform
 * is skipped (a duplicate there), as importing it would overwrite that gig.
 */
@Service
public final class ImportPlanner {

    private ImportPlanner() {
    }

    public static ImportPlan plan(Collection<Gig> catalog, Collection<Publication> publications,
                                  Collection<ImportedGig> imported, Map<Platform, String> failures,
                                  Platforms platforms) {
        Map<GigMatch, Gig> localByMatch = new LinkedHashMap<>();
        catalog.forEach(g -> localByMatch.putIfAbsent(g.match(), g));
        Links links = Links.of(publications);
        Grouping grouping = new Grouping(localByMatch, links, platforms);
        imported.stream().sorted(byPrecedence(platforms)).forEach(grouping::add);
        suggestMatches(localByMatch.values(), links.platformsByGig(), grouping.withLocal, grouping.withoutLocal);
        return new ImportPlan(grouping.proposals(), grouping.alreadyLinked, grouping.skipped, failures);
    }

    /** An event on a platform, by its id there. */
    private record PlatformRef(Platform platform, String ref) {
    }

    /**
     * What the catalog already links: the platform events, and per catalog gig (by its identity)
     * the platforms it is on.
     */
    private record Links(Set<PlatformRef> refs, Map<GigId, Set<Platform>> platformsByGig) {

        static Links of(Collection<Publication> publications) {
            Set<PlatformRef> refs = new HashSet<>();
            Map<GigId, Set<Platform>> platformsByGig = new HashMap<>();
            for (Publication p : publications) {
                if (p.hasExternalRef()) {
                    refs.add(new PlatformRef(p.platform(), p.externalRef()));
                }
                platformsByGig.computeIfAbsent(p.gigId(), _ -> new HashSet<>()).add(p.platform());
            }
            return new Links(refs, platformsByGig);
        }

        boolean linked(ImportedGig copy) {
            return refs.contains(new PlatformRef(copy.platform(), copy.externalRef()));
        }

        boolean on(GigId id, Platform platform) {
            return platformsByGig.getOrDefault(id, Set.of()).contains(platform);
        }
    }

    /**
     * The platform gigs grouped by what concert they are — with the catalog gig of that day and
     * venue, or on their own — counting those already linked and leaving out (with the reason) those
     * that would overwrite a gig already linked there.
     *
     * <p>Grouped by {@link GigMatch} and not by identity: a copy read from a platform is a gig this
     * app has never seen, so its identity is one nothing else shares. The day and the venue are all
     * two sources have in common.
     */
    private static final class Grouping {

        private final Map<GigMatch, Gig> localByMatch;
        private final Links links;
        private final Platforms platforms;
        private final Map<GigMatch, Group> withLocal = new LinkedHashMap<>();
        private final Map<GigMatch, Group> withoutLocal = new LinkedHashMap<>();
        private final List<String> skipped = new ArrayList<>();
        private int alreadyLinked;

        Grouping(Map<GigMatch, Gig> localByMatch, Links links, Platforms platforms) {
            this.localByMatch = localByMatch;
            this.links = links;
            this.platforms = platforms;
        }

        void add(ImportedGig copy) {
            if (links.linked(copy)) {
                alreadyLinked++;
                return;
            }
            GigMatch match = copy.gig().match();
            Gig local = localByMatch.get(match);
            Map<GigMatch, Group> groups = local != null ? withLocal : withoutLocal;
            boolean takenOnPlatform = local != null && links.on(local.id(), copy.platform());
            Group existing = groups.get(match);
            if (takenOnPlatform || (existing != null && existing.copies.containsKey(copy.platform()))) {
                skipped.add(describe(copy, platforms) + " — another " + platforms.name(copy.platform())
                        + " event already has this date and venue");
                return;
            }
            groups.computeIfAbsent(match, _ -> new Group(local)).copies.put(copy.platform(), copy);
        }

        /** Newest first. */
        List<ImportProposal> proposals() {
            List<ImportProposal> proposals = new ArrayList<>();
            withLocal.values().forEach(g -> proposals.add(g.toProposal(platforms)));
            withoutLocal.values().forEach(g -> proposals.add(g.toProposal(platforms)));
            proposals.sort(Comparator.comparing(ImportProposal::date).reversed());
            return proposals;
        }
    }

    /** The platforms' copies whose details win first on import, first. */
    private static Comparator<ImportedGig> byPrecedence(Platforms platforms) {
        return Comparator.comparingInt((ImportedGig c) -> platforms.traits(c.platform()).importPrecedence())
                .thenComparing(ImportedGig::platform);
    }

    private static void suggestMatches(Collection<Gig> catalog, Map<GigId, Set<Platform>> linkedPlatforms,
                                       Map<GigMatch, Group> withLocal, Map<GigMatch, Group> withoutLocal) {
        for (GigMatch match : new ArrayList<>(withoutLocal.keySet())) {
            Group lonely = withoutLocal.get(match);
            if (lonely == null || lonely.copies.size() != 1) {
                continue;
            }
            ImportedGig copy = lonely.copies.values().iterator().next();
            Platform platform = copy.platform();

            List<Gig> localCandidates = catalog.stream()
                    .filter(g -> sameDayAndCity(g, copy.gig()))
                    .filter(g -> !linkedPlatforms.getOrDefault(g.id(), Set.of()).contains(platform))
                    .filter(g -> !withLocal.containsKey(g.match())
                            || !withLocal.get(g.match()).copies.containsKey(platform))
                    .toList();
            if (localCandidates.size() == 1) {
                Gig local = localCandidates.getFirst();
                Group target = withLocal.computeIfAbsent(local.match(), _ -> new Group(local));
                target.copies.put(platform, copy);
                target.suggested = true;
                withoutLocal.remove(match);
                continue;
            }
            if (!localCandidates.isEmpty()) {
                continue;                   // ambiguous: let the user sort it out by hand
            }

            List<Map.Entry<GigMatch, Group>> otherPlatform = withoutLocal.entrySet().stream()
                    .filter(e -> !e.getKey().equals(match))
                    .filter(e -> !e.getValue().copies.containsKey(platform))
                    .filter(e -> e.getValue().copies.values().stream().allMatch(c -> sameDayAndCity(c.gig(), copy.gig())))
                    .toList();
            if (otherPlatform.size() == 1) {
                Group target = otherPlatform.getFirst().getValue();
                target.copies.put(platform, copy);
                target.suggested = true;
                withoutLocal.remove(match);
            }
        }
    }

    private static boolean sameDayAndCity(Gig a, Gig b) {
        LocalDate dayA = a.schedule().startDate();
        return dayA.equals(b.schedule().startDate()) && CityName.same(a.location().city(), b.location().city());
    }

    private static String describe(ImportedGig copy, Platforms platforms) {
        Gig g = copy.gig();
        return platforms.name(copy.platform()) + " " + g.schedule().startDate() + " " + g.title()
                + " (" + g.location().displayVenue() + ", " + g.location().city() + ")";
    }

    private static final class Group {
        private final Gig local;
        private final Map<Platform, ImportedGig> copies = new HashMap<>();
        private boolean suggested;

        private Group(Gig local) {
            this.local = local;
        }

        private ImportProposal toProposal(Platforms platforms) {
            return new ImportProposal(local, copies.values().stream().sorted(byPrecedence(platforms)).toList(),
                    suggested);
        }
    }
}
