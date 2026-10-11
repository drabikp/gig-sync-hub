package sk.drabikp.bzscraper.importing.domain;

import sk.drabikp.bzscraper.gig.domain.Admission;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.gig.domain.GigSchedule;
import sk.drabikp.bzscraper.gig.domain.platform.Platforms;
import sk.drabikp.bzscraper.importing.domain.ImportProposal.Version;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Builds the catalog gig from the chosen version of an imported gig. The chosen version
 * decides what the gig is (title, time, place); details it lacks — end time, lineup,
 * description, links, poster — are filled from the other versions (catalog first, then the
 * platforms by import precedence). The entry comes from a version whose platform shows the
 * entry ({@code carriesAdmission}) when there is one. A gig cancelled in any version stays
 * cancelled.
 */
public final class GigMerge {

    private GigMerge() {
    }

    /**
     * {@code keeping}: the catalog gig this import is an edit of, or null when the gig is new to the
     * catalog. {@code platforms}: which of them show the entry (the others' "free" means nothing).
     */
    public static Gig merge(Gig keeping, Version chosen, List<Version> versions, Platforms platforms) {
        List<Version> order = new ArrayList<>();
        order.add(chosen);
        versions.stream().filter(v -> !v.equals(chosen)).forEach(order::add);
        Gig base = chosen.gig();
        GigId id = keeping != null ? keeping.id() : base.id();

        List<String> lineup = base.lineup().isEmpty()
                ? order.stream().map(v -> v.gig().lineup()).filter(l -> !l.isEmpty()).findFirst().orElse(List.of())
                : base.lineup();
        Admission admission = order.stream()
                .filter(v -> v.platform() == null || platforms.traits(v.platform()).carriesAdmission())
                .map(v -> v.gig().admission())
                .findFirst()
                .orElse(base.admission());
        boolean cancelled = order.stream().anyMatch(v -> v.gig().cancelled());

        GigSchedule schedule = base.schedule().end() != null ? base.schedule()
                : new GigSchedule(base.schedule().start(), order.stream()
                        .map(v -> v.gig().schedule().end())
                        .filter(end -> end != null && end.isAfter(base.schedule().start()))
                        .findFirst().orElse(null), base.schedule().slot());

        return new Gig(id, base.title(), schedule, base.location(), lineup, admission,
                firstText(order, Gig::description), firstText(order, Gig::facebookUrl),
                firstText(order, Gig::ticketUrl), firstText(order, Gig::posterImageUrl), cancelled);
    }

    private static String firstText(List<Version> order, Function<Gig, String> field) {
        return order.stream()
                .map(v -> field.apply(v.gig()))
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .findFirst()
                .orElse(null);
    }
}
