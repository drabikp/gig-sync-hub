package sk.drabikp.bzscraper.importing.domain;

import sk.drabikp.bzscraper.gig.domain.CityName;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.GigSchedule;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One gig the import would bring in or link: the platform copies that are the same
 * concert, and the catalog gig they belong to (null when the gig is new to the catalog).
 *
 * <p>Copies are grouped automatically when their identity matches (same date and venue).
 * A {@code suggested} proposal was grouped only on date and city because the venue is
 * written differently — the user confirms it, otherwise each copy is imported on its own.
 *
 * <p>No jMolecules stereotype on purpose: One gig an import would bring in or link — it HOLDS an aggregate rather than being a value
 * of one, which is what a read model does and what {@code valueObjectsMustNotReferToIdentifiables}
 * rightly refuses to call a value object.
 */
public record ImportProposal(Gig local, List<ImportedGig> copies, boolean suggested) {

    /** One version of the gig's details, from the catalog ({@code platform} null) or a platform. */
    public record Version(Platform platform, Gig gig) {
    }

    /** {@code copies} in the platforms' import precedence (the planner orders them). */
    public ImportProposal {
        copies = List.copyOf(copies);
    }

    public boolean inCatalog() {
        return local != null;
    }

    public LocalDate date() {
        return (local != null ? local : copies.getFirst().gig()).schedule().startDate();
    }

    /** The distinct versions of the gig's details: catalog first, then by platform. */
    public List<Version> versions() {
        List<Version> all = new ArrayList<>();
        if (local != null) {
            all.add(new Version(null, local));
        }
        copies.forEach(c -> all.add(new Version(c.platform(), c.gig())));
        List<Version> distinct = new ArrayList<>();
        for (Version v : all) {
            if (distinct.stream().noneMatch(d -> sameDetails(d.gig(), v.gig()))) {
                distinct.add(v);
            }
        }
        return distinct;
    }

    /** True when the versions disagree on what the gig is (title, time, venue, city). */
    public boolean hasConflict() {
        return versions().size() > 1;
    }

    /** The version used unless the user picks another: the catalog's, else the first platform's by precedence. */
    public Version defaultVersion() {
        return versions().getFirst();
    }

    /**
     * Compares only what every platform carries — title, start, end, venue, city. Not every
     * platform shows the entry or the lineup, so those would differ for no reason. An end time
     * known on one side only is missing information, not a disagreement.
     */
    static boolean sameDetails(Gig a, Gig b) {
        return norm(a.title()).equals(norm(b.title()))
                && a.schedule().start().toInstant().equals(b.schedule().start().toInstant())
                && sameEnd(a.schedule(), b.schedule())
                && norm(a.location().displayVenue()).equals(norm(b.location().displayVenue()))
                && CityName.same(a.location().city(), b.location().city());
    }

    private static boolean sameEnd(GigSchedule a, GigSchedule b) {
        return a.end() == null || b.end() == null || a.end().toInstant().equals(b.end().toInstant());
    }

    static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
