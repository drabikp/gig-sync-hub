package sk.drabikp.bzscraper.calendar.adapter.out.ics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import sk.drabikp.bzscraper.calendar.adapter.out.profile.ProfileFile;
import sk.drabikp.bzscraper.calendar.application.port.out.BandCalendarStore;
import sk.drabikp.bzscraper.calendar.domain.BandCalendar;
import sk.drabikp.bzscraper.calendar.domain.CalendarGigDrafter;
import sk.drabikp.bzscraper.calendar.domain.event.CalendarEvent;
import sk.drabikp.bzscraper.calendar.domain.event.CalendarEventKind;
import sk.drabikp.bzscraper.calendar.domain.rules.BandProfile;
import sk.drabikp.bzscraper.calendar.domain.rules.CalendarClassification;
import sk.drabikp.bzscraper.calendar.domain.rules.CalendarEventClassifier;
import sk.drabikp.bzscraper.calendar.domain.rules.ProfileRule;
import sk.drabikp.bzscraper.calendar.domain.rules.RuleOrigin;
import sk.drabikp.bzscraper.gig.domain.BandId;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reads the REAL band calendar and prints how the shipped presets plus the band's own
 * rules sort it — for checking rule changes by eye. Read-only. Skipped unless
 * {@code CAL_LIVE=true}; reads {@code CAL_URL} (the private iCal address — never commit
 * it), optional {@code CAL_PROFILE} (the band's rules file) and {@code CAL_CATALOG_DAYS}
 * (a file of ISO dates the catalog has gigs on, one per line).
 */
@EnabledIfEnvironmentVariable(named = "CAL_LIVE", matches = "true")
class CalendarClassificationLiveTest {

    @Test
    void sorts_the_real_calendar() throws Exception {
        BandCalendar calendar = new BandCalendar(BandId.FIRST, System.getenv("CAL_URL"), ZoneId.of("Europe/Prague"));
        List<CalendarEvent> events = new IcsCalendarFeed(new OneCalendar(calendar)).read(BandId.FIRST);

        List<ProfileRule> rules = new ArrayList<>();
        for (String preset : List.of("base", "sk-cz")) {
            rules.addAll(ProfileFile.parse(new ClassPathResource("calendar/" + preset + ".profile")
                    .getContentAsString(StandardCharsets.UTF_8), RuleOrigin.PRESET));
        }
        String own = System.getenv("CAL_PROFILE");
        if (own != null && !own.isBlank()) {
            rules.addAll(ProfileFile.parse(Files.readString(Path.of(own)), RuleOrigin.USER));
        }
        String daysFile = System.getenv("CAL_CATALOG_DAYS");
        Set<LocalDate> catalogDays = daysFile == null || daysFile.isBlank() ? Set.of()
                : Files.readAllLines(Path.of(daysFile)).stream().map(String::strip).filter(s -> !s.isEmpty())
                .map(LocalDate::parse).collect(Collectors.toSet());

        BandProfile profile = new BandProfile(rules, new BandProfile.Thresholds(4, -1, -4));
        List<CalendarClassification> results = CalendarEventClassifier.classify(events, profile, catalogDays, Map.of());

        Map<CalendarEventKind, Long> counts = results.stream()
                .collect(Collectors.groupingBy(CalendarClassification::kind, Collectors.counting()));
        System.out.println(events.size() + " events: " + counts);
        for (CalendarEventKind kind : List.of(CalendarEventKind.GIG, CalendarEventKind.UNSURE)) {
            System.out.println("== " + kind);
            results.stream().filter(c -> c.kind() == kind)
                    .sorted(Comparator.comparing(c -> c.event().start()))
                    .forEach(CalendarClassificationLiveTest::print);
        }
        System.out.println("== NOT_GIG close to the line (score > -4)");
        results.stream().filter(c -> c.kind() == CalendarEventKind.NOT_GIG && c.score() > -4)
                .sorted(Comparator.comparing(c -> c.event().start()))
                .forEach(CalendarClassificationLiveTest::print);

        System.out.println("== GIG as a draft (date, show time, venue | city | country) — notes are not printed");
        results.stream().filter(c -> c.kind() == CalendarEventKind.GIG)
                .sorted(Comparator.comparing(c -> c.event().start()))
                .map(c -> CalendarGigDrafter.draft(c.event(), profile))
                .forEach(d -> System.out.printf("  %s %-5s %-40.40s | %-24.24s | %s%n", d.date(),
                        d.showTime() == null ? "-" : d.showTime(), d.venue() == null ? "-" : d.venue(),
                        d.city() == null ? "-" : d.city(), d.country() == null ? "-" : d.country()));

        assertThat(events).isNotEmpty();
    }

    private static void print(CalendarClassification c) {
        System.out.printf("  %s  %-48.48s %3d %-9s %s%n", c.event().day(), c.event().title(), c.score(),
                c.status(), c.reasons().stream().map(r -> r.text() + " " + r.weight()).collect(Collectors.joining("; ")));
    }

    /** The one calendar read here. */
    private record OneCalendar(BandCalendar calendar) implements BandCalendarStore {

        @Override
        public Optional<BandCalendar> of(BandId band) {
            return Optional.of(calendar).filter(c -> c.band().equals(band));
        }

        @Override
        public void save(BandCalendar calendar) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(BandId band) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int reseal() {
            return 0;
        }
    }
}
