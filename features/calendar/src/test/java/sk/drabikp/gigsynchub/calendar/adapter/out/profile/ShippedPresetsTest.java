package sk.drabikp.gigsynchub.calendar.adapter.out.profile;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import sk.drabikp.gigsynchub.calendar.domain.event.CalendarEvent;
import sk.drabikp.gigsynchub.calendar.domain.event.CalendarEventKind;
import sk.drabikp.gigsynchub.calendar.domain.event.CalendarEventStatus;
import sk.drabikp.gigsynchub.calendar.domain.rules.BandProfile;
import sk.drabikp.gigsynchub.calendar.domain.rules.CalendarClassification;
import sk.drabikp.gigsynchub.calendar.domain.rules.CalendarEventClassifier;
import sk.drabikp.gigsynchub.calendar.domain.rules.ProfileRule;
import sk.drabikp.gigsynchub.calendar.domain.rules.RuleKind;
import sk.drabikp.gigsynchub.calendar.domain.rules.RuleOrigin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static sk.drabikp.gigsynchub.calendar.domain.event.CalendarEventKind.GIG;
import static sk.drabikp.gigsynchub.calendar.domain.event.CalendarEventKind.NOT_GIG;
import static sk.drabikp.gigsynchub.calendar.domain.event.CalendarEventKind.UNSURE;

/**
 * The shipped rules (base + sk-cz) on made-up events written the way Slovak/Czech band
 * calendars write them. The band's own rule here is a made-up member, "Jana".
 */
class ShippedPresetsTest {

    private static final LocalDateTime EVENING = LocalDateTime.of(2026, 9, 18, 19, 0);

    private static List<ProfileRule> rules() throws IOException {
        List<ProfileRule> rules = new ArrayList<>();
        for (String preset : List.of("base", "sk-cz")) {
            rules.addAll(ProfileFile.parse(new ClassPathResource("calendar/" + preset + ".profile")
                    .getContentAsString(StandardCharsets.UTF_8), RuleOrigin.PRESET));
        }
        rules.add(new ProfileRule(RuleKind.MEMBER, "jana", -4, RuleOrigin.USER, true));
        return rules;
    }

    private static int ids;

    private static CalendarEvent event(String title, String notes) {
        return new CalendarEvent("e" + ++ids, title, "", notes, EVENING, EVENING.plusHours(3), false, false, false,
                CalendarEventStatus.CONFIRMED, null);
    }

    private static CalendarEvent allDay(String title, int days) {
        LocalDate day = EVENING.toLocalDate();
        return new CalendarEvent("e" + ++ids, title, "", "", day.atStartOfDay(), day.plusDays(days).atStartOfDay(),
                true, false, true, CalendarEventStatus.CONFIRMED, null);
    }

    private static CalendarClassification classify(CalendarEvent event) throws IOException {
        return CalendarEventClassifier.classify(List.of(event),
                new BandProfile(rules(), new BandProfile.Thresholds(4, -1, -4)), Set.of(), Map.of()).getFirst();
    }

    private static CalendarEventKind kind(String title) throws IOException {
        return classify(event(title, "")).kind();
    }

    @Test
    void the_shipped_rules_parse() throws IOException {
        assertThat(rules()).hasSizeGreaterThan(15);
    }

    @Test
    void rehearsals_travel_calls_and_shoots_are_not_gigs() throws IOException {
        for (String title : List.of("Skúška (full)", "SKUSKA", "REH 2/4", "cesta Brno", "Zkouška", "Porada - Call",
                "Call porada", "Fotenie", "Točenie klipu", "Nahrávanie", "HOTEL Nové Adalbertinum", "Volať Novák",
                "Podcast \"Z garáže\"", "ZL Video pozvánka", "Pozvánka na koncert")) {
            assertThat(kind(title)).as(title).isEqualTo(NOT_GIG);
        }
    }

    @Test
    void absences_and_members_events_are_not_gigs() throws IOException {
        assertThat(kind("Jana nemôže")).isEqualTo(NOT_GIG);
        assertThat(kind("Jana - Rock Lietava")).isEqualTo(NOT_GIG);        // a member's other band
        assertThat(kind("Tomáš nemůže")).isEqualTo(NOT_GIG);               // not a member, but absent
        assertThat(classify(allDay("Dovolenka", 8)).kind()).isEqualTo(NOT_GIG);
    }

    @Test
    void a_day_sheet_makes_a_gig() throws IOException {
        CalendarClassification gig = classify(event("Moto Big Bang Luková",
                "Vykládka+setup: 16:30\nSoundcheck: 17:30\nShowtime: 18:30 - 19:30\nMerch: Naty"));

        assertThat(gig.kind()).isEqualTo(GIG);
        assertThat(gig.status()).isEqualTo(CalendarEventStatus.CONFIRMED);
    }

    @Test
    void the_older_day_sheet_style_counts_too() throws IOException {
        assertThat(classify(event("Akcia v RK", "Pokec: dve kapely pred nami\nČas predbežne: 20:00\nCena: 300 €"))
                .kind()).isEqualTo(GIG);
    }

    @Test
    void cancelled_and_tentative_gigs_keep_their_status() throws IOException {
        assertThat(classify(event("ZRUŠENÉ Traktor OpenAir Plzeň", "Showtime: 19:15")).status())
                .isEqualTo(CalendarEventStatus.CANCELLED);
        assertThat(classify(event("PREDBEŽNE: Petřvaldský otvírák", "Showtime: 20:00")).status())
                .isEqualTo(CalendarEventStatus.TENTATIVE);
        assertThat(classify(event("Rockové Sady", "Stav : Potvrdené\nShowtime: 18:00")).status())
                .isEqualTo(CalendarEventStatus.CONFIRMED);
        assertThat(classify(event("Rot Kart Fest", "Čas predbežne: 17:30")).status())
                .isEqualTo(CalendarEventStatus.CONFIRMED);
    }

    @Test
    void a_festival_name_alone_or_a_visit_is_left_to_the_user() throws IOException {
        assertThat(kind("FRYY Fest - Bakov nad Jizerou")).isEqualTo(UNSURE);
        assertThat(kind("WostrovFest - návšteva kapely")).isEqualTo(UNSURE);
    }
}
