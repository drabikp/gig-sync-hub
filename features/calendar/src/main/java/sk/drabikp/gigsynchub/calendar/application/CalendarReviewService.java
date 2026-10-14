package sk.drabikp.gigsynchub.calendar.application;

import org.jmolecules.architecture.hexagonal.Application;
import sk.drabikp.gigsynchub.calendar.application.port.in.ForgetOldCalendarCopyUseCase;
import sk.drabikp.gigsynchub.calendar.application.port.in.ReviewCalendarUseCase;
import sk.drabikp.gigsynchub.calendar.application.port.out.BandProfileStore;
import sk.drabikp.gigsynchub.calendar.application.port.out.CalendarDecisionStore;
import sk.drabikp.gigsynchub.calendar.application.port.out.CalendarFeed;
import sk.drabikp.gigsynchub.calendar.application.port.out.CalendarLinkStore;
import sk.drabikp.gigsynchub.calendar.application.port.out.CalendarSnapshotStore;
import sk.drabikp.gigsynchub.calendar.application.port.out.CalendarUnavailableException;
import sk.drabikp.gigsynchub.calendar.domain.CalendarCatalogMatcher;
import sk.drabikp.gigsynchub.calendar.domain.CalendarChanges;
import sk.drabikp.gigsynchub.calendar.domain.CalendarGigDraft;
import sk.drabikp.gigsynchub.calendar.domain.CalendarGigDrafter;
import sk.drabikp.gigsynchub.calendar.domain.CalendarOverview;
import sk.drabikp.gigsynchub.calendar.domain.CalendarRow;
import sk.drabikp.gigsynchub.calendar.domain.CatalogMatch;
import sk.drabikp.gigsynchub.calendar.domain.KnownCalendarEvent;
import sk.drabikp.gigsynchub.calendar.domain.event.CalendarEvent;
import sk.drabikp.gigsynchub.calendar.domain.event.CalendarEventKind;
import sk.drabikp.gigsynchub.calendar.domain.rules.BandProfile;
import sk.drabikp.gigsynchub.calendar.domain.rules.CalendarClassification;
import sk.drabikp.gigsynchub.calendar.domain.rules.CalendarEventClassifier;
import sk.drabikp.gigsynchub.calendar.domain.rules.ProfileRule;
import sk.drabikp.gigsynchub.gig.application.BadRequestException;
import sk.drabikp.gigsynchub.gig.application.port.out.GigRepository;
import sk.drabikp.gigsynchub.gig.application.port.out.LiveUpdates;
import sk.drabikp.gigsynchub.gig.application.port.out.Transactions;
import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.gig.domain.DateRange;
import sk.drabikp.gigsynchub.gig.domain.Gig;
import sk.drabikp.gigsynchub.gig.domain.GigId;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Calendar orchestrator. Reads the calendar, classifies its events with the stored band
 * profile ({@link CalendarEventClassifier}, the catalog's gig days as evidence), keeps the
 * user's verdicts, and saves each read so the next one shows what is new, changed or gone
 * ({@link CalendarChanges}). Matches every event against the catalog
 * ({@link CalendarCatalogMatcher}) and pre-fills a gig form from it ({@link CalendarGigDrafter});
 * bringing gigs into the catalog is {@link CalendarCatalogService}'s. Never writes the calendar.
 */
@Application
public class CalendarReviewService implements ReviewCalendarUseCase, ForgetOldCalendarCopyUseCase {

    private final CalendarFeed feed;
    private final BandProfileStore profileStore;
    private final CalendarDecisionStore decisionStore;
    private final CalendarSnapshotStore snapshotStore;
    private final CalendarLinkStore linkStore;
    private final GigRepository gigRepository;
    private final Transactions transactions;
    private final Clock clock;
    private final BandProfile.Thresholds thresholds;
    private final LiveUpdates live;

    public CalendarReviewService(CalendarFeed feed, BandProfileStore profileStore, CalendarDecisionStore decisionStore,
                                 CalendarSnapshotStore snapshotStore, CalendarLinkStore linkStore,
                                 GigRepository gigRepository, Transactions transactions, Clock clock,
                                 BandProfile.Thresholds thresholds, LiveUpdates live) {
        this.live = live;
        this.feed = feed;
        this.profileStore = profileStore;
        this.decisionStore = decisionStore;
        this.snapshotStore = snapshotStore;
        this.linkStore = linkStore;
        this.gigRepository = gigRepository;
        this.transactions = transactions;
        this.clock = clock;
        this.thresholds = thresholds;
    }

    @Override
    public int forgetRemovedBefore(Instant when) {
        return snapshotStore.forgetRemovedBefore(when);
    }

    @Override
    public boolean configured(BandId band) {
        return feed.configured(band);
    }

    @Override
    public CalendarOverview read(BandId band) throws CalendarUnavailableException {
        List<CalendarEvent> fresh = feed.read(band);
        Instant now = clock.instant();
        transactions.inTransaction(() -> {
            List<CalendarClassification> classified = classify(band, fresh, profile(band),
                    around(band, fresh));
            snapshotStore.saveRead(band, CalendarChanges.afterRead(snapshotStore.all(band), classified, now), now);
        });
        changed(band);
        return overview(band);
    }

    @Override
    public CalendarOverview overview(BandId band) {
        Map<String, KnownCalendarEvent> known = snapshotStore.all(band);
        List<CalendarEvent> shown = known.values().stream()
                .filter(k -> !k.removed() || k.change() != null)
                .map(KnownCalendarEvent::event)
                .toList();
        BandProfile profile = profile(band);
        List<Gig> gigs = around(band, shown);
        Map<GigId, Gig> catalog = new HashMap<>();
        gigs.forEach(gig -> catalog.put(gig.id(), gig));
        Map<String, GigId> links = linkStore.all(band);
        alsoLinkedFromOutside(band, links, catalog);
        LocalDate today = LocalDate.now(clock);
        List<CalendarRow> rows = new ArrayList<>();
        for (CalendarClassification c : classify(band, shown, profile, gigs)) {
            KnownCalendarEvent k = known.get(c.event().id());
            CalendarGigDraft draft = CalendarGigDrafter.draft(c.event(), profile);
            CatalogMatch match = CalendarCatalogMatcher.match(draft, c.status(), k.removed(), links.get(k.id()),
                    catalog, today);
            rows.add(new CalendarRow(c, draft, match, k.change(), k.removed()));
        }
        return CalendarOverview.of(snapshotStore.lastRead(band).orElse(null), rows, today);
    }

    @Override
    public void decide(BandId band, String eventId, CalendarEventKind verdict) {
        if (verdict == CalendarEventKind.UNSURE) {
            throw new BadRequestException("a verdict is gig or not a gig");
        }
        decisionStore.decide(band, eventId, verdict);
        changed(band);
    }

    @Override
    public void forget(BandId band, String eventId) {
        decisionStore.forget(band, eventId);
        changed(band);
    }

    @Override
    public void seen(BandId band, String eventId) {
        transactions.inTransaction(() -> snapshotStore.find(band, eventId).ifPresent(k -> markSeen(band, k)));
        changed(band);
    }

    @Override
    public void seenAll(BandId band) {
        transactions.inTransaction(() -> snapshotStore.all(band).values().stream()
                .filter(k -> k.change() != null)
                .forEach(k -> markSeen(band, k)));
        changed(band);
    }

    private void markSeen(BandId band, KnownCalendarEvent known) {
        if (known.removed()) {
            snapshotStore.remove(band, known.id());
            linkStore.unlink(band, known.id());
        } else if (known.change() != null) {
            snapshotStore.save(band, known.seen());
        }
    }

    @Override
    public List<ProfileRule> rules(BandId band) {
        return profileStore.rules(band);
    }

    /** The band's calendar page reads the calendar again. */
    private void changed(BandId band) {
        live.changed(band, LiveUpdates.Topic.CALENDAR);
    }

    /**
     * The catalog the calendar is compared against: the gigs whose days fall in the window the
     * events themselves cover, and not the band's whole history.
     *
     * <p>Everything this service asks of the catalog is about an event's own day — the classifier
     * asks "is there a gig that day", the matcher "which gigs are on this day" — so a window that
     * covers every event's day gives the same answers as the whole catalog, by construction rather
     * than by luck. A day either side because a show that starts after midnight is drafted onto
     * the next day, and a gig may run into the window from just before it.
     */
    private List<Gig> around(BandId band, Collection<CalendarEvent> events) {
        LocalDate first = null;
        LocalDate last = null;
        for (CalendarEvent event : events) {
            LocalDate day = event.day();
            first = first == null || day.isBefore(first) ? day : first;
            last = last == null || day.isAfter(last) ? day : last;
        }
        return first == null ? List.of()
                : gigRepository.findCovering(band, new DateRange(first.minusDays(1), last.plusDays(1)));
    }

    /**
     * A gig an event is LINKED to, when the gig has since been moved outside the window — the link
     * is the band's own decision and must not look broken ("gone from the catalog") because the
     * read was narrowed. Usually none, and then nothing is asked.
     */
    private void alsoLinkedFromOutside(BandId band, Map<String, GigId> links, Map<GigId, Gig> catalog) {
        List<GigId> missing = links.values().stream().filter(id -> !catalog.containsKey(id)).distinct().toList();
        if (!missing.isEmpty()) {
            gigRepository.findAllByIds(band, missing).forEach(gig -> catalog.put(gig.id(), gig));
        }
    }

    private List<CalendarClassification> classify(BandId band, List<CalendarEvent> events, BandProfile profile,
                                                  Collection<Gig> gigs) {
        Set<LocalDate> catalogDays = gigs.stream().flatMap(gig -> gig.schedule().days().stream())
                .collect(Collectors.toSet());
        return CalendarEventClassifier.classify(events, profile, catalogDays, decisionStore.all(band));
    }

    private BandProfile profile(BandId band) {
        return new BandProfile(profileStore.rules(band), thresholds);
    }
}
