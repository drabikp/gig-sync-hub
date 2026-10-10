package sk.drabikp.bzscraper.importing.domain;

import sk.drabikp.bzscraper.gig.domain.CityName;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.gig.domain.platform.Platforms;
import sk.drabikp.bzscraper.gig.domain.platform.Publication;

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
 *   <li>The rest are grouped by gig identity (date + venue): with the catalog gig of the
 *       same identity, and with the same gig from the other platform.</li>
 *   <li>A gig still on its own is <em>suggested</em> as the same concert as a catalog gig,
 *       or as a gig from the other platform, on the same date in the same city — but
 *       only when exactly one such candidate exists. The user confirms suggestions.</li>
 * </ol>
 * A platform gig whose identity is taken by another gig already linked on that platform
 * is skipped (a duplicate there), as importing it would overwrite the catalog gig.
 */
public final class ImportPlanner {

    private ImportPlanner() {
    }

    public static ImportPlan plan(Collection<Gig> catalog, Collection<Publication> publications,
                                  Collection<ImportedGig> imported, Map<Platform, String> failures,
                                  Platforms platforms) {
        Map<GigId, Gig> localById = new LinkedHashMap<>();
        catalog.forEach(g -> localById.putIfAbsent(g.id(), g));
        Links links = Links.of(publications);
        Grouping grouping = new Grouping(localById, links, platforms);
        imported.stream().sorted(byPrecedence(platforms)).forEach(grouping::add);
        suggestMatches(localById.values(), links.platformsByGig(), grouping.withLocal, grouping.withoutLocal);
        return new ImportPlan(grouping.proposals(), grouping.alreadyLinked, grouping.skipped, failures);
    }

    /** An event on a platform, by its id there. */
    private record PlatformRef(Platform platform, String ref) {
    }

    /** What the catalog already links: the platform events, and per gig the platforms it is on. */
    private record Links(Set<PlatformRef> refs, Map<GigId, Set<Platform>> platformsByGig) {

        static Links of(Collection<Publication> publications) {
            Set<PlatformRef> refs = new HashSet<>();
            Map<GigId, Set<Platform>> platformsByGig = new HashMap<>();
            for (Publication p : publications) {
                if (p.hasExternalRef()) {
                    refs.add(new PlatformRef(p.platform(), p.externalRef()));
                }
                platformsByGig.computeIfAbsent(p.gigId(), id -> new HashSet<>()).add(p.platform());
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
     * The platform gigs grouped by identity — with the catalog gig of that identity, or on their own —
     * counting those already linked and leaving out (with the reason) those that would overwrite a
     * gig already linked there.
     */
    private static final class Grouping {

        private final Map<GigId, Gig> localById;
        private final Links links;
        private final Platforms platforms;
        private final Map<GigId, Group> withLocal = new LinkedHashMap<>();
        private final Map<GigId, Group> withoutLocal = new LinkedHashMap<>();
        private final List<String> skipped = new ArrayList<>();
        private int alreadyLinked;

        Grouping(Map<GigId, Gig> localById, Links links, Platforms platforms) {
            this.localById = localById;
            this.links = links;
            this.platforms = platforms;
        }

        void add(ImportedGig copy) {
            if (links.linked(copy)) {
                alreadyLinked++;
                return;
            }
            GigId id = copy.gig().id();
            Gig local = localById.get(id);
            Map<GigId, Group> groups = local != null ? withLocal : withoutLocal;
            boolean takenOnPlatform = local != null && links.on(id, copy.platform());
            Group existing = groups.get(id);
            if (takenOnPlatform || (existing != null && existing.copies.containsKey(copy.platform()))) {
                skipped.add(describe(copy, platforms) + " — another " + platforms.name(copy.platform())
                        + " event already has this date and venue");
                return;
            }
            groups.computeIfAbsent(id, k -> new Group(local)).copies.put(copy.platform(), copy);
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
                                       Map<GigId, Group> withLocal, Map<GigId, Group> withoutLocal) {
        for (GigId id : new ArrayList<>(withoutLocal.keySet())) {
            Group lonely = withoutLocal.get(id);
            if (lonely == null || lonely.copies.size() != 1) {
                continue;
            }
            ImportedGig copy = lonely.copies.values().iterator().next();
            Platform platform = copy.platform();

            List<Gig> localCandidates = catalog.stream()
                    .filter(g -> sameDayAndCity(g, copy.gig()))
                    .filter(g -> !linkedPlatforms.getOrDefault(g.id(), Set.of()).contains(platform))
                    .filter(g -> !withLocal.containsKey(g.id()) || !withLocal.get(g.id()).copies.containsKey(platform))
                    .toList();
            if (localCandidates.size() == 1) {
                Gig local = localCandidates.getFirst();
                Group target = withLocal.computeIfAbsent(local.id(), k -> new Group(local));
                target.copies.put(platform, copy);
                target.suggested = true;
                withoutLocal.remove(id);
                continue;
            }
            if (!localCandidates.isEmpty()) {
                continue;                   // ambiguous: let the user sort it out by hand
            }

            List<Map.Entry<GigId, Group>> otherPlatform = withoutLocal.entrySet().stream()
                    .filter(e -> !e.getKey().equals(id))
                    .filter(e -> !e.getValue().copies.containsKey(platform))
                    .filter(e -> e.getValue().copies.values().stream().allMatch(c -> sameDayAndCity(c.gig(), copy.gig())))
                    .toList();
            if (otherPlatform.size() == 1) {
                Group target = otherPlatform.getFirst().getValue();
                target.copies.put(platform, copy);
                target.suggested = true;
                withoutLocal.remove(id);
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
