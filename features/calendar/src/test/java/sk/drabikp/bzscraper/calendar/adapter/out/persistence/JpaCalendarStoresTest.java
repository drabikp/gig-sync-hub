package sk.drabikp.bzscraper.calendar.adapter.out.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import sk.drabikp.bzscraper.calendar.domain.CalendarChange;
import sk.drabikp.bzscraper.calendar.domain.KnownCalendarEvent;
import sk.drabikp.bzscraper.calendar.domain.event.CalendarEvent;
import sk.drabikp.bzscraper.calendar.domain.event.CalendarEventKind;
import sk.drabikp.bzscraper.calendar.domain.event.CalendarEventStatus;
import sk.drabikp.bzscraper.calendar.domain.rules.ProfileRule;
import sk.drabikp.bzscraper.calendar.domain.rules.RuleKind;
import sk.drabikp.bzscraper.calendar.domain.rules.RuleOrigin;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.gig.domain.TestBands;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static sk.drabikp.bzscraper.gig.domain.TestBands.BAND;
import static sk.drabikp.bzscraper.gig.domain.TestBands.OTHER;

@SpringBootTest
@Transactional
class JpaCalendarStoresTest {

    @Autowired
    private JpaBandProfileStore profiles;

    @Autowired
    private JpaCalendarDecisionStore decisions;

    @Autowired
    private JpaCalendarSnapshotStore snapshots;

    @Autowired
    private JpaCalendarLinkStore links;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void anotherBand() {
        jdbc.update(TestBands.INSERT_OTHER);
    }

    private static ProfileRule user(RuleKind kind, String value, int weight) {
        return new ProfileRule(kind, value, weight, RuleOrigin.USER, true);
    }

    @Test
    void replacing_one_origin_keeps_the_others_and_the_order() {
        List<ProfileRule> presets = profiles.rules(BAND).stream().filter(r -> r.origin() == RuleOrigin.PRESET)
                .toList();

        profiles.replace(BAND, RuleOrigin.USER, List.of(user(RuleKind.MEMBER, "jana", -4),
                user(RuleKind.REPEATING, null, -8)));
        profiles.replace(BAND, RuleOrigin.USER, List.of(user(RuleKind.MEMBER, "jana|janka", -4),
                new ProfileRule(RuleKind.TITLE_CONTAINS, "fest", 2, RuleOrigin.USER, false)));

        assertThat(profiles.rules(BAND)).filteredOn(r -> r.origin() == RuleOrigin.USER).containsExactly(
                user(RuleKind.MEMBER, "jana|janka", -4),
                new ProfileRule(RuleKind.TITLE_CONTAINS, "fest", 2, RuleOrigin.USER, false));
        assertThat(profiles.rules(BAND)).filteredOn(r -> r.origin() == RuleOrigin.PRESET).isEqualTo(presets);
    }

    @Test
    void the_shipped_rules_are_everyones_and_a_bands_own_only_its() {
        profiles.replaceShipped(List.of(new ProfileRule(RuleKind.REPEATING, null, -8, RuleOrigin.PRESET, true)));
        profiles.replace(BAND, RuleOrigin.USER, List.of(user(RuleKind.MEMBER, "jana", -4)));
        profiles.replace(OTHER, RuleOrigin.USER, List.of(user(RuleKind.MEMBER, "fero", -4)));

        assertThat(profiles.rules(BAND)).containsExactly(
                new ProfileRule(RuleKind.REPEATING, null, -8, RuleOrigin.PRESET, true), user(RuleKind.MEMBER, "jana", -4));
        assertThat(profiles.rules(OTHER)).containsExactly(
                new ProfileRule(RuleKind.REPEATING, null, -8, RuleOrigin.PRESET, true), user(RuleKind.MEMBER, "fero", -4));
    }

    @Test
    void rules_of_another_origin_are_refused() {
        assertThatThrownBy(() -> profiles.replace(BAND, RuleOrigin.LEARNED, List.of(user(RuleKind.MEMBER, "jana", -4))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> profiles.replace(BAND, RuleOrigin.PRESET, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void remembers_changes_and_forgets_verdicts() {
        decisions.decide(BAND, "uid-1", CalendarEventKind.GIG);
        decisions.decide(BAND, "uid-2", CalendarEventKind.NOT_GIG);
        decisions.decide(BAND, "uid-1", CalendarEventKind.NOT_GIG);
        decisions.decide(OTHER, "uid-2", CalendarEventKind.GIG);          // the same event uid in another band
        decisions.forget(BAND, "uid-2");
        decisions.forget(BAND, "never-decided");

        assertThat(decisions.all(BAND)).containsExactly(Map.entry("uid-1", CalendarEventKind.NOT_GIG));
        assertThat(decisions.all(OTHER)).containsExactly(Map.entry("uid-2", CalendarEventKind.GIG));
    }

    @Test
    void the_saved_calendar_keeps_every_field_and_the_unseen_change() {
        Instant firstRead = Instant.parse("2026-10-01T08:00:00Z");
        Instant secondRead = Instant.parse("2026-10-08T08:00:00Z");
        LocalDateTime start = LocalDateTime.of(2026, 11, 20, 16, 0);
        CalendarEvent event = new CalendarEvent("uid-1", "Fest", "Klub, Praha", "Showtime: 20:30\nCena: 500",
                start, start.plusHours(8), false, false, true, CalendarEventStatus.TENTATIVE, firstRead);
        CalendarChange change = new CalendarChange(CalendarChange.Type.CHANGED,
                Set.of(CalendarChange.Field.NOTES, CalendarChange.Field.TIME), CalendarEventKind.UNSURE, secondRead);
        KnownCalendarEvent known = new KnownCalendarEvent(event, CalendarEventKind.GIG, firstRead, null, change);

        snapshots.saveRead(BAND, List.of(known), secondRead);
        snapshots.saveRead(OTHER, List.of(known.seen()), firstRead);

        assertThat(snapshots.find(BAND, "uid-1")).contains(known);
        assertThat(snapshots.lastRead(BAND)).contains(secondRead);

        snapshots.save(BAND, known.seen());
        assertThat(snapshots.all(BAND).get("uid-1").change()).isNull();
        assertThat(snapshots.lastRead(BAND)).as("marking seen is not a read").contains(secondRead);

        snapshots.remove(BAND, "uid-1");
        assertThat(snapshots.all(BAND)).isEmpty();
        assertThat(snapshots.all(OTHER)).as("the other band's copy").containsOnlyKeys("uid-1");
        assertThat(snapshots.lastRead(OTHER)).contains(firstRead);
    }

    @Test
    void links_follow_a_gig_whose_identity_changed() {
        GigId gig = new GigId(LocalDate.of(2026, 11, 20), "klub");
        GigId moved = new GigId(LocalDate.of(2026, 11, 21), "klub");
        links.link(BAND, "uid-1", gig);
        links.link(BAND, "uid-2", new GigId(LocalDate.of(2026, 12, 1), "@brno"));
        links.link(OTHER, "uid-9", gig);                                  // another band's gig that day

        links.move(BAND, gig, moved);
        links.unlink(BAND, "uid-2");

        assertThat(links.all(BAND)).containsExactly(Map.entry("uid-1", moved));
        assertThat(links.all(OTHER)).containsExactly(Map.entry("uid-9", gig));
    }
}
