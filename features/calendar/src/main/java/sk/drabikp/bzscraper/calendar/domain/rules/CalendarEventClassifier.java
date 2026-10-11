package sk.drabikp.bzscraper.calendar.domain.rules;

import org.jmolecules.ddd.annotation.Service;
import sk.drabikp.bzscraper.calendar.domain.event.CalendarEvent;
import sk.drabikp.bzscraper.calendar.domain.event.CalendarEventKind;
import sk.drabikp.bzscraper.calendar.domain.event.CalendarEventStatus;
import sk.drabikp.bzscraper.calendar.domain.rules.CalendarClassification.Reason;
import sk.drabikp.bzscraper.calendar.domain.rules.CalendarClassification.Why;
import sk.drabikp.bzscraper.gig.domain.TextFold;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

/**
 * Pure domain service: sorts calendar events into gig / unsure / not a gig with a band's
 * {@link BandProfile}. Every matching weighted rule adds its weight to the event's score
 * and is kept as a reason; the thresholds turn the score into a suggestion. The user's
 * remembered verdict (by event id) always wins over the suggestion.
 * <ul>
 *   <li>An event with some evidence for a gig and none strongly against is never
 *       suggested as "not a gig" — it is left to the user.</li>
 *   <li>{@link RuleKind#CATALOG_GIG_SAME_DAY} doesn't count when strong evidence against
 *       already matched: travel and rehearsals on a gig day stay what they are.</li>
 * </ul>
 * The status (confirmed / tentative / cancelled) is decided separately, from the
 * calendar's own status and the profile's status rules.
 */
@Service
public final class CalendarEventClassifier {

    private static final Duration TRAVEL_ARRIVAL = Duration.ofHours(1);

    private CalendarEventClassifier() {
    }

    public static List<CalendarClassification> classify(List<CalendarEvent> events, BandProfile profile,
                                                         Set<LocalDate> catalogDays,
                                                         Map<String, CalendarEventKind> decisions) {
        Context context = new Context(profile, catalogDays,
                events.stream().collect(Collectors.groupingBy(e -> TextFold.fold(e.title()), Collectors.counting())),
                events.stream().collect(Collectors.groupingBy(CalendarEvent::day)));
        return events.stream().map(e -> classify(e, context, decisions.get(e.id()))).toList();
    }

    private record Context(BandProfile profile, Set<LocalDate> catalogDays, Map<String, Long> titleCounts,
                           Map<LocalDate, List<CalendarEvent>> byDay) {
    }

    private static CalendarClassification classify(CalendarEvent event, Context context, CalendarEventKind verdict) {
        BandProfile profile = context.profile();
        BandProfile.Thresholds thresholds = profile.thresholds();

        List<Reason> reasons = new ArrayList<>();
        for (ProfileRule rule : profile.rules()) {
            if (rule.enabled() && rule.kind().weighted() && rule.kind() != RuleKind.CATALOG_GIG_SAME_DAY) {
                match(rule, event, context).ifPresent(reasons::add);
            }
        }
        boolean strongAgainst = reasons.stream().anyMatch(r -> r.weight() <= thresholds.strongNegative());
        if (!strongAgainst && context.catalogDays().contains(event.day())) {
            profile.enabled(RuleKind.CATALOG_GIG_SAME_DAY).stream().findFirst()
                    .ifPresent(rule -> reasons.add(Reason.of(Why.CATALOG_GIG_SAME_DAY, rule.weight())));
        }
        int score = reasons.stream().mapToInt(Reason::weight).sum();
        boolean anyFor = reasons.stream().anyMatch(r -> r.weight() > 0);

        CalendarEventKind suggested;
        if (score >= thresholds.gigScore()) {
            suggested = CalendarEventKind.GIG;
        } else if (score <= thresholds.notGigScore() && (!anyFor || strongAgainst)) {
            suggested = CalendarEventKind.NOT_GIG;
        } else {
            suggested = CalendarEventKind.UNSURE;
        }

        CalendarEventStatus status = status(event, profile, reasons);
        boolean decided = verdict != null && verdict != CalendarEventKind.UNSURE;
        return new CalendarClassification(event, decided ? verdict : suggested, suggested, decided, status, score,
                reasons);
    }

    private static Optional<Reason> match(ProfileRule rule, CalendarEvent event, Context context) {
        int weight = rule.weight();
        return switch (rule.kind()) {
            case TITLE_STARTS_WITH -> startsWith(rule, event.title()).map(w -> new Reason(Why.TITLE_STARTS_WITH, w, weight));
            case MEMBER -> startsWith(rule, event.title()).map(w -> new Reason(Why.MEMBER, w, weight));
            case TITLE_CONTAINS -> contains(rule, event.title()).map(w -> new Reason(Why.TITLE_CONTAINS, w, weight));
            case NOTES_CONTAIN -> contains(rule, event.notes()).map(w -> new Reason(Why.NOTES_CONTAIN, w, weight));
            case NOTES_LABEL -> rule.alternatives().stream()
                    .filter(label -> NotesLabels.label(label).matcher(TextFold.fold(event.notes())).find())
                    .findFirst().map(label -> new Reason(Why.NOTES_LABEL, label, weight));
            case REPEATING -> event.repeating() ? Optional.of(Reason.of(Why.REPEATING, weight)) : Optional.empty();
            case LONGER_THAN_DAYS -> {
                long days = Duration.between(event.start(), event.end()).toDays();
                yield days > rule.number() ? Optional.of(new Reason(Why.DAYS_LONG, String.valueOf(days), weight))
                        : Optional.empty();
            }
            case ALL_DAY_FREE -> event.allDay() && event.shownAsFree()
                    ? Optional.of(Reason.of(Why.ALL_DAY_FREE, weight)) : Optional.empty();
            case TITLE_REPEATED -> {
                long uses = context.titleCounts().getOrDefault(TextFold.fold(event.title()), 0L);
                yield uses >= rule.number() ? Optional.of(new Reason(Why.TITLE_REPEATED, String.valueOf(uses), weight))
                        : Optional.empty();
            }
            case TRAVEL_LEADS_TO -> travelTo(event, context).map(t -> new Reason(Why.TRAVEL_LEADS_TO, t.title(), weight));
            // the catalog's gig that day is scored by the classifier itself; role kinds carry no weight
            case CATALOG_GIG_SAME_DAY, TRAVEL, CANCELLED_TITLE, CANCELLED_NOTES, TENTATIVE_TITLE, TENTATIVE_NOTES,
                 CONFIRMED_FIELD, SHOWTIME_LABEL -> Optional.empty();
        };
    }

    private static Optional<CalendarEvent> travelTo(CalendarEvent event, Context context) {
        List<ProfileRule> travel = context.profile().enabled(RuleKind.TRAVEL);
        if (isTravel(event, travel)) {
            return Optional.empty();
        }
        return context.byDay().getOrDefault(event.day(), List.of()).stream()
                .filter(other -> other != event && isTravel(other, travel))
                .filter(other -> (!event.location().isEmpty()
                        && TextFold.fold(other.location()).equals(TextFold.fold(event.location())))
                        || Duration.between(other.end(), event.start()).abs().compareTo(TRAVEL_ARRIVAL) <= 0)
                .findFirst();
    }

    private static boolean isTravel(CalendarEvent event, Collection<ProfileRule> travelRules) {
        return travelRules.stream().anyMatch(rule -> startsWith(rule, event.title()).isPresent());
    }

    private static CalendarEventStatus status(CalendarEvent event, BandProfile profile, List<Reason> reasons) {
        if (event.calendarStatus() == CalendarEventStatus.CANCELLED) {
            reasons.add(Reason.of(Why.CANCELLED_IN_CALENDAR, 0));
            return CalendarEventStatus.CANCELLED;
        }
        Optional<Reason> cancelled = firstMatch(profile.enabled(RuleKind.CANCELLED_TITLE),
                rule -> prefix(rule, event.title()).map(w -> new Reason(Why.CANCELLED_TITLE, w, 0)))
                .or(() -> firstMatch(profile.enabled(RuleKind.CANCELLED_NOTES),
                        rule -> contains(rule, event.notes()).map(w -> new Reason(Why.CANCELLED_NOTES, w, 0))));
        if (cancelled.isPresent()) {
            reasons.add(cancelled.get());
            return CalendarEventStatus.CANCELLED;
        }
        Optional<Reason> tentative = event.calendarStatus() == CalendarEventStatus.TENTATIVE
                ? Optional.of(Reason.of(Why.TENTATIVE_IN_CALENDAR, 0))
                : firstMatch(profile.enabled(RuleKind.TENTATIVE_TITLE),
                        rule -> prefix(rule, event.title()).map(w -> new Reason(Why.TENTATIVE_TITLE, w, 0)))
                .or(() -> firstMatch(profile.enabled(RuleKind.TENTATIVE_NOTES),
                        rule -> contains(rule, event.notes()).map(w -> new Reason(Why.TENTATIVE_NOTES, w, 0))))
                .or(() -> firstMatch(profile.enabled(RuleKind.CONFIRMED_FIELD), rule -> unconfirmedField(rule, event)));
        if (tentative.isPresent()) {
            reasons.add(tentative.get());
            return CalendarEventStatus.TENTATIVE;
        }
        return CalendarEventStatus.CONFIRMED;
    }

    private static Optional<Reason> unconfirmedField(ProfileRule rule, CalendarEvent event) {
        Matcher field = NotesLabels.field(rule.fieldLabel()).matcher(TextFold.fold(event.notes()));
        if (field.find() && !field.group(1).startsWith(rule.fieldPrefix())) {
            return Optional.of(new Reason(Why.TENTATIVE_FIELD, rule.fieldLabel() + ": " + field.group(1), 0));
        }
        return Optional.empty();
    }

    private static Optional<Reason> firstMatch(List<ProfileRule> rules, Function<ProfileRule, Optional<Reason>> test) {
        return rules.stream().map(test).flatMap(Optional::stream).findFirst();
    }

    /** The title without leading punctuation ("?Songwriting", "- Fest") starts with an alternative. */
    private static Optional<String> startsWith(ProfileRule rule, String text) {
        String folded = TextFold.fold(text).replaceFirst("^[^\\p{L}\\p{N}]+", "");
        return rule.alternatives().stream().filter(folded::startsWith).findFirst();
    }

    /** The text as written starts with an alternative — status markers can be punctuation ("???"). */
    private static Optional<String> prefix(ProfileRule rule, String text) {
        String folded = TextFold.fold(text);
        return rule.alternatives().stream().filter(folded::startsWith).findFirst();
    }

    private static Optional<String> contains(ProfileRule rule, String text) {
        String folded = TextFold.fold(text);
        return rule.alternatives().stream().filter(folded::contains).findFirst();
    }

}
