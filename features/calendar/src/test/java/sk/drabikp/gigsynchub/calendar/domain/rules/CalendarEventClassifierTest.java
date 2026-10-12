package sk.drabikp.gigsynchub.calendar.domain.rules;

import org.junit.jupiter.api.Test;
import sk.drabikp.gigsynchub.calendar.domain.event.CalendarEvent;
import sk.drabikp.gigsynchub.calendar.domain.event.CalendarEventKind;
import sk.drabikp.gigsynchub.calendar.domain.event.CalendarEventStatus;
import sk.drabikp.gigsynchub.calendar.domain.rules.CalendarClassification.Reason;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static sk.drabikp.gigsynchub.calendar.domain.event.CalendarEventKind.GIG;
import static sk.drabikp.gigsynchub.calendar.domain.event.CalendarEventKind.NOT_GIG;
import static sk.drabikp.gigsynchub.calendar.domain.event.CalendarEventKind.UNSURE;
import static sk.drabikp.gigsynchub.calendar.domain.rules.ProfileRule.preset;

class CalendarEventClassifierTest {

    private static final LocalDateTime EVENING = LocalDateTime.of(2026, 9, 18, 19, 0);
    private static final AtomicInteger IDS = new AtomicInteger();

    private static final List<ProfileRule> RULES = List.of(
            preset(RuleKind.TITLE_STARTS_WITH, "skuska|cesta", -6),
            preset(RuleKind.MEMBER, "jana", -4),
            preset(RuleKind.TITLE_CONTAINS, "nemoze", -4),
            preset(RuleKind.NOTES_LABEL, "showtime|cas predbezne", 5),
            preset(RuleKind.TITLE_CONTAINS, "fest|koncert", 3),
            preset(RuleKind.REPEATING, null, -10),
            preset(RuleKind.LONGER_THAN_DAYS, "3", -3),
            preset(RuleKind.ALL_DAY_FREE, null, -1),
            preset(RuleKind.TITLE_REPEATED, "4", -3),
            preset(RuleKind.CATALOG_GIG_SAME_DAY, null, 2),
            preset(RuleKind.TRAVEL_LEADS_TO, null, 3),
            preset(RuleKind.TRAVEL, "cesta", 0),
            preset(RuleKind.CANCELLED_TITLE, "zrusen", 0),
            preset(RuleKind.TENTATIVE_TITLE, "predbezne|?", 0),
            preset(RuleKind.TENTATIVE_NOTES, "v jednani", 0),
            preset(RuleKind.CONFIRMED_FIELD, "stav=potvrd", 0));

    private static CalendarEvent event(String title, String notes, String location,
                                       LocalDateTime start, LocalDateTime end) {
        return new CalendarEvent("e" + IDS.incrementAndGet(), title, location, notes, start, end, false, false, false,
                CalendarEventStatus.CONFIRMED, null);
    }

    private static CalendarEvent event(String title) {
        return event(title, "", "", EVENING, EVENING.plusHours(2));
    }

    private static CalendarEvent event(String title, String notes) {
        return event(title, notes, "", EVENING, EVENING.plusHours(2));
    }

    private static CalendarEvent allDay(String title, LocalDate from, int days) {
        return new CalendarEvent("e" + IDS.incrementAndGet(), title, "", "", from.atStartOfDay(),
                from.plusDays(days).atStartOfDay(), true, false, true, CalendarEventStatus.CONFIRMED, null);
    }

    private static List<CalendarClassification> classify(List<ProfileRule> rules, Set<LocalDate> catalogDays,
                                                         Map<String, CalendarEventKind> decisions,
                                                         CalendarEvent... events) {
        return CalendarEventClassifier.classify(List.of(events),
                new BandProfile(rules, new BandProfile.Thresholds(4, -1, -4)), catalogDays, decisions);
    }

    private static CalendarClassification one(CalendarEvent event) {
        return classify(RULES, Set.of(), Map.of(), event).getFirst();
    }

    @Test
    void a_day_sheet_and_a_gig_word_make_a_gig_and_say_why() {
        CalendarClassification c = one(event("Rock Fest Hranice", "Soundcheck: 17:00\nShowtime: 19:00"));

        assertThat(c.kind()).isEqualTo(GIG);
        assertThat(c.decidedByUser()).isFalse();
        assertThat(c.score()).isEqualTo(8);
        assertThat(c.reasons()).extracting(Reason::text, Reason::weight).containsExactly(
                tuple("notes have \"showtime:\"", 5), tuple("title contains \"fest\"", 3));
    }

    @Test
    void rehearsals_and_members_events_are_not_gigs() {
        assertThat(one(event("Skúška")).kind()).isEqualTo(NOT_GIG);
        assertThat(one(event("Jana nemôže")).kind()).isEqualTo(NOT_GIG);
    }

    @Test
    void case_accents_and_leading_punctuation_do_not_matter() {
        assertThat(one(event("SKÚŠKA (full)")).kind()).isEqualTo(NOT_GIG);
        assertThat(one(event("- skuska")).kind()).isEqualTo(NOT_GIG);
        assertThat(one(event("Gig", "ČAS PREDBEŽNE: 20:00")).reasons()).extracting(Reason::text, Reason::weight)
                .contains(tuple("notes have \"cas predbezne:\"", 5));
    }

    @Test
    void a_note_label_must_start_a_word_and_may_lose_its_spaces() {
        assertThat(one(event("Gig", "Časpredbežne: 20:00")).score()).isEqualTo(5);
        assertThat(one(event("Gig", "Talkshowtime: 20:00")).score()).isZero();
        assertThat(one(event("Gig", "Showtime 20:00")).score()).isZero();     // no colon: not a label
    }

    @Test
    void a_gig_word_alone_is_left_to_the_user() {
        CalendarClassification c = one(event("Koncert Hlinsko"));

        assertThat(c.kind()).isEqualTo(UNSURE);
        assertThat(c.score()).isEqualTo(3);
    }

    @Test
    void evidence_for_a_gig_without_strong_evidence_against_is_never_silently_dropped() {
        // 5-day "festival trip" offer: +3 fest, −3 long, −1 all-day free = −1
        CalendarClassification offer = one(allDay("Nightghost OpenAir Fest", LocalDate.of(2027, 6, 21), 5));

        assertThat(offer.score()).isEqualTo(-1);
        assertThat(offer.kind()).isEqualTo(UNSURE);
    }

    @Test
    void strong_evidence_against_wins_over_a_gig_word() {
        // a member's own event: +3 fest, −4 member
        assertThat(one(event("Jana Metalfest")).kind()).isEqualTo(NOT_GIG);
    }

    @Test
    void the_catalog_gig_that_day_counts_but_not_for_travel_on_that_day() {
        CalendarEvent club = event("Barrák s Salamandra");
        CalendarEvent travel = event("cesta Ostrava", "", "", EVENING.minusHours(6), EVENING.minusHours(3));

        List<CalendarClassification> results = classify(RULES, Set.of(EVENING.toLocalDate()), Map.of(), club, travel);

        assertThat(results.get(0).reasons()).extracting(Reason::text, Reason::weight).containsExactly(tuple("the catalog has a gig that day", 2));
        assertThat(results.get(0).kind()).isEqualTo(UNSURE);
        assertThat(results.get(1).kind()).isEqualTo(NOT_GIG);
        assertThat(results.get(1).reasons()).extracting(Reason::text).doesNotContain("the catalog has a gig that day");
    }

    @Test
    void travel_arriving_just_before_or_going_to_the_same_place_counts() {
        CalendarEvent arriving = event("cesta Zlín", "", "", EVENING.minusHours(4), EVENING.minusMinutes(30));
        CalendarEvent show = event("Eufory + Snaefell");
        CalendarEvent samePlace = event("cesta Klub", "", "Zámecký klub, Hranice", EVENING.minusHours(9),
                EVENING.minusHours(7));
        CalendarEvent club = event("Snaefell", "", "Zámecký klub, Hranice", EVENING, EVENING.plusHours(2));

        assertThat(classify(RULES, Set.of(), Map.of(), arriving, show).get(1).reasons()).extracting(Reason::text, Reason::weight)
                .containsExactly(tuple("travel \"cesta Zlín\" leads to it", 3));
        assertThat(classify(RULES, Set.of(), Map.of(), samePlace, club).get(1).reasons()).extracting(Reason::text, Reason::weight)
                .containsExactly(tuple("travel \"cesta Klub\" leads to it", 3));
    }

    @Test
    void travel_far_from_the_event_does_not_count() {
        CalendarEvent morning = event("cesta Zlín", "", "", EVENING.minusHours(12), EVENING.minusHours(10));

        assertThat(classify(RULES, Set.of(), Map.of(), morning, event("Gig")).get(1).reasons()).isEmpty();
    }

    @Test
    void repeating_long_and_much_used_titles_count_against() {
        CalendarEvent weekly = new CalendarEvent("call", "Call", "", "", EVENING, EVENING.plusHours(1), false, true,
                false, CalendarEventStatus.CONFIRMED, null);
        assertThat(one(weekly).reasons()).extracting(Reason::text, Reason::weight).containsExactly(tuple("repeating event", -10));
        assertThat(one(allDay("Sri Lanka", LocalDate.of(2021, 9, 8), 13)).reasons()).extracting(Reason::text, Reason::weight)
                .containsExactly(tuple("13 days long", -3), tuple("all-day, time shown as free", -1));

        CalendarEvent[] meetings = {event("Porada"), event("porada"), event("PORADA"), event("Porada")};
        assertThat(classify(RULES, Set.of(), Map.of(), meetings).getFirst().reasons()).extracting(Reason::text, Reason::weight)
                .containsExactly(tuple("title used 4 times", -3));
    }

    @Test
    void the_status_comes_from_the_calendar_and_the_status_rules() {
        assertThat(one(event("ZRUŠENÉ Traktor OpenAir")).status()).isEqualTo(CalendarEventStatus.CANCELLED);
        assertThat(one(event("PREDBEŽNE: Petřvaldský otvírák")).status()).isEqualTo(CalendarEventStatus.TENTATIVE);
        assertThat(one(event("???Nightghost OpenAir???")).status()).isEqualTo(CalendarEventStatus.TENTATIVE);
        assertThat(one(event("Štramák Fest", "V jednaní . . .")).status()).isEqualTo(CalendarEventStatus.TENTATIVE);
        assertThat(one(event("Rockové Sady", "Stav :  Potvrdené")).status()).isEqualTo(CalendarEventStatus.CONFIRMED);
        assertThat(one(event("Rockové Sady", "Stav: dohaduje sa")).status()).isEqualTo(CalendarEventStatus.TENTATIVE);

        CalendarEvent cancelledInCalendar = new CalendarEvent("x", "Fest", "", "", EVENING, EVENING, false, false,
                false, CalendarEventStatus.CANCELLED, null);
        assertThat(one(cancelledInCalendar).status()).isEqualTo(CalendarEventStatus.CANCELLED);
    }

    @Test
    void a_provisional_time_does_not_make_the_gig_tentative() {
        CalendarClassification c = one(event("Rot Kart Fest", "Čas predbežne: 17:30 / Dĺžka hrania: 60 min"));

        assertThat(c.status()).isEqualTo(CalendarEventStatus.CONFIRMED);
        assertThat(c.kind()).isEqualTo(GIG);
    }

    @Test
    void the_users_verdict_wins_and_the_suggestion_is_kept() {
        CalendarEvent visit = event("Wostrov Fest - návšteva");

        CalendarClassification c = classify(RULES, Set.of(), Map.of(visit.id(), NOT_GIG), visit).getFirst();

        assertThat(c.kind()).isEqualTo(NOT_GIG);
        assertThat(c.suggested()).isEqualTo(UNSURE);
        assertThat(c.decidedByUser()).isTrue();
    }

    @Test
    void a_switched_off_rule_does_nothing() {
        List<ProfileRule> rules = new ArrayList<>(RULES);
        rules.set(0, new ProfileRule(RuleKind.TITLE_STARTS_WITH, "skuska|cesta", -6, RuleOrigin.PRESET, false));

        assertThat(classify(rules, Set.of(), Map.of(), event("Skúška")).getFirst().score()).isZero();
    }
}
