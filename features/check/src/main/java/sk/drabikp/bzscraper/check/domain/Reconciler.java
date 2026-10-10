package sk.drabikp.bzscraper.check.domain;

import sk.drabikp.bzscraper.gig.domain.Address;
import sk.drabikp.bzscraper.gig.domain.CityName;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.gig.domain.TextFold;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.gig.domain.platform.PlatformTraits;
import sk.drabikp.bzscraper.gig.domain.platform.Publication;
import sk.drabikp.bzscraper.importing.domain.ImportedGig;
import sk.drabikp.bzscraper.sync.domain.SyncTask;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Compares what a platform shows with the catalog — the desired state — for every gig
 * published there: the event is gone, or its day, time, name, venue, town, country or
 * cancelled state, or place differs. What a platform is expected to show differently (its
 * {@link PlatformTraits}) is not a drift: a platform without a cancelled state removes a
 * cancelled gig, one that lists the band's slot shows that instead of the whole event; a
 * town may carry a district ("Košice I") and a venue the platform's longer name. Gigs with platform work still queued are skipped — they
 * are about to change.
 */
public final class Reconciler {

    /** How far from the gig's town a platform may place it. */
    static final double NEAR_KM = 25;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private Reconciler() {
    }

    /**
     * @param catalog  the catalog's gigs
     * @param records  the platform's publication records
     * @param onPlatform every event the platform lists (upcoming and past)
     * @param syncing  gigs with platform work still queued or running
     */
    public static List<Drift> drifts(PlatformTraits traits, Collection<Gig> catalog, Collection<Publication> records,
                                     Collection<ImportedGig> onPlatform, Set<GigId> syncing) {
        Platform platform = traits.platform();
        Map<GigId, Gig> gigs = catalog.stream().collect(Collectors.toMap(Gig::id, Function.identity(), (a, b) -> a));
        Map<String, ImportedGig> listed = onPlatform.stream().filter(i -> i.platform().equals(platform))
                .collect(Collectors.toMap(ImportedGig::externalRef, Function.identity(), (a, b) -> a));
        List<Drift> drifts = new ArrayList<>();
        for (Publication record : records) {
            Gig gig = gigs.get(record.gigId());
            if (!record.platform().equals(platform) || !record.hasExternalRef() || gig == null
                    || syncing.contains(record.gigId())) {
                continue;
            }
            ImportedGig copy = listed.get(record.externalRef());
            String label = SyncTask.labelOf(gig);
            if (copy == null) {
                boolean removedAsCancelled = gig.cancelled() && !traits.keepsCancelledEvents();
                if (!removedAsCancelled) {
                    drifts.add(new Drift(platform, gig.id(), label, record.externalRef(), Drift.Kind.MISSING, List.of()));
                }
                continue;
            }
            List<Drift.Difference> differences = differences(traits, gig, copy);
            if (!differences.isEmpty()) {
                drifts.add(new Drift(platform, gig.id(), label, record.externalRef(), Drift.Kind.DIFFERENT, differences));
            }
        }
        return drifts;
    }

    /** How many of the platform's events no publication record points to. */
    public static int unlinked(Platform platform, Collection<Publication> records, Collection<ImportedGig> onPlatform) {
        Set<String> linked = records.stream().filter(r -> r.platform().equals(platform) && r.hasExternalRef())
                .map(Publication::externalRef).collect(Collectors.toSet());
        return (int) onPlatform.stream().filter(i -> i.platform().equals(platform) && !linked.contains(i.externalRef())).count();
    }

    static List<Drift.Difference> differences(PlatformTraits traits, Gig gig, ImportedGig listed) {
        Gig copy = listed.gig();
        List<Drift.Difference> differences = new ArrayList<>();
        String name = traits.displayName();
        ZonedDateTime expected = traits.listsBandSlot() ? gig.schedule().showStart() : gig.schedule().start();
        ZonedDateTime shown = copy.schedule().start().withZoneSameInstant(expected.getZone());
        if (!expected.toLocalDate().equals(shown.toLocalDate())) {
            differences.add(new Drift.Difference(Drift.Field.DATE, expected.toLocalDate().toString(),
                    shown.toLocalDate().toString(), "date: catalog " + expected.format(DAY) + ", " + name + " "
                    + shown.format(DAY)));
        } else if (!expected.toLocalTime().equals(shown.toLocalTime())) {
            differences.add(new Drift.Difference(Drift.Field.TIME, expected.format(TIME), shown.format(TIME),
                    "time: catalog " + expected.format(TIME) + ", " + name + " " + shown.format(TIME)));
        }
        if (!fold(gig.title()).equals(fold(copy.title()))) {
            differences.add(new Drift.Difference(Drift.Field.NAME, gig.title(), copy.title(),
                    "name: catalog '" + gig.title() + "', " + name + " '" + copy.title() + "'"));
        }
        if (!sameVenue(gig.location().venue(), copy.location().venue())) {
            differences.add(new Drift.Difference(Drift.Field.VENUE, gig.location().venue(), copy.location().venue(),
                    "venue: catalog " + quoted(gig.location().venue()) + ", " + name + " "
                    + quoted(copy.location().venue())));
        }
        if (!sameTown(gig.location().city(), copy.location().city())) {
            differences.add(new Drift.Difference(Drift.Field.TOWN, gig.location().city(), copy.location().city(),
                    "town: catalog " + gig.location().city() + ", " + name + " " + copy.location().city()));
        } else if (gig.location().country() != null && copy.location().country() != null
                && gig.location().country() != copy.location().country()) {
            differences.add(new Drift.Difference(Drift.Field.COUNTRY, gig.location().country().name(),
                    copy.location().country().name(), "country: catalog " + gig.location().countryName() + ", "
                    + name + " " + copy.location().countryName() + " (a town of the same name elsewhere?)"));
        } else {
            farFromTown(gig, listed).ifPresent(km -> differences.add(new Drift.Difference(Drift.Field.PLACE,
                    gig.location().city() + " (" + gig.location().address().district() + ")",
                    String.format(Locale.ROOT, "%.0f", km), String.format(Locale.ROOT,
                    "place: %s put it %.0f km from %s (%s)", name, km, gig.location().city(),
                    gig.location().address().district()))));
        }
        if (traits.keepsCancelledEvents() && gig.cancelled() != copy.cancelled()) {
            differences.add(new Drift.Difference(Drift.Field.CANCELLED, String.valueOf(gig.cancelled()),
                    String.valueOf(copy.cancelled()), gig.cancelled() ? "cancelled in the catalog, not on " + name
                    : "cancelled on " + name + ", not in the catalog"));
        }
        return differences;
    }

    /**
     * How far the platform put the event from the gig's town, when that is more than
     * {@value #NEAR_KM} km — known when the town was picked from the place search and the
     * platform shows coordinates (a platform that geocodes text once picked a same-named town).
     */
    private static Optional<Double> farFromTown(Gig gig, ImportedGig listed) {
        Address town = gig.location().address();
        if (town == null || !town.resolved()) {
            return Optional.empty();
        }
        OptionalDouble km = listed.coordinates().map(town::kilometresTo).orElse(OptionalDouble.empty());
        return km.isPresent() && km.getAsDouble() > NEAR_KM ? Optional.of(km.getAsDouble()) : Optional.empty();
    }

    /**
     * Equal, or one name contains the other ("Secret Garden" — "Secret Garden - Piváreň -
     * Koncerty"); a placeholder ("-", "TBA") is no venue.
     */
    private static boolean sameVenue(String a, String b) {
        String x = venueKey(a);
        String y = venueKey(b);
        if (x.isEmpty() || y.isEmpty()) {
            return x.equals(y);
        }
        return x.contains(y) || y.contains(x);
    }

    private static String venueKey(String venue) {
        String key = fold(venue);
        return key.matches("[-–?.]*|tba|tbd|n/?a") ? "" : key;
    }

    /** The same town, also with a district after the name ("Košice I", "Praha 5"). */
    private static boolean sameTown(String a, String b) {
        String x = CityName.key(a);
        String y = CityName.key(b);
        return x.equals(y) || x.startsWith(y + " ") || y.startsWith(x + " ");
    }

    private static String fold(String text) {
        return text == null ? "" : TextFold.fold(text).replaceAll("\\s+", " ").trim();
    }

    private static String quoted(String text) {
        return venueKey(text).isEmpty() ? "none" : "'" + text + "'";
    }
}
