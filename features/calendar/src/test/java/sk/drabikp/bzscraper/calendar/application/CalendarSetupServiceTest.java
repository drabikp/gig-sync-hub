package sk.drabikp.bzscraper.calendar.application;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.calendar.application.port.in.CalendarErrorCode;
import sk.drabikp.bzscraper.calendar.application.port.in.CalendarSetupUseCase.Connection;
import sk.drabikp.bzscraper.calendar.application.port.in.CalendarSetupUseCase.RuleDraft;
import sk.drabikp.bzscraper.calendar.domain.rules.ProfileRule;
import sk.drabikp.bzscraper.calendar.domain.rules.RuleKind;
import sk.drabikp.bzscraper.calendar.domain.rules.RuleOrigin;
import sk.drabikp.bzscraper.gig.application.UserFacingException;
import sk.drabikp.bzscraper.gig.application.port.out.LiveUpdates;
import sk.drabikp.bzscraper.gig.application.port.out.TestLiveUpdates;

import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static sk.drabikp.bzscraper.gig.domain.TestBands.BAND;
import static sk.drabikp.bzscraper.gig.domain.TestBands.OTHER;

class CalendarSetupServiceTest {

    private static final String ADDRESS = "webcal://1.1.1.1/ical/secret-part/basic.ics";

    private final CalendarFakes.Calendars calendars = new CalendarFakes.Calendars();
    private final CalendarFakes.Profiles profiles = new CalendarFakes.Profiles();
    private final TestLiveUpdates live = new TestLiveUpdates();
    private final CalendarSetupService setup = new CalendarSetupService(calendars, profiles, live,
            ZoneId.of("Europe/Prague"));

    @Test
    void a_band_connects_its_calendar_and_only_whose_it_is_is_shown_again() {
        assertThat(setup.connection(BAND)).isEqualTo(new Connection(false, null, "Europe/Prague"));

        Connection connected = setup.connect(BAND, ADDRESS, "Europe/Bratislava");

        assertThat(connected).isEqualTo(new Connection(true, "1.1.1.1", "Europe/Bratislava"));
        assertThat(connected.toString()).doesNotContain("secret-part");
        assertThat(calendars.of(BAND).orElseThrow().address()).startsWith("https://").contains("secret-part");
        assertThat(calendars.of(BAND).orElseThrow().toString()).doesNotContain("secret-part");
        assertThat(setup.connection(OTHER).connected()).isFalse();
        assertThat(live.updates).containsExactly(new TestLiveUpdates.Update(BAND, LiveUpdates.Topic.CALENDAR));
    }

    @Test
    void an_address_or_zone_that_isnt_one_is_refused() {
        assertThatThrownBy(() -> setup.connect(BAND, "1.1.1.1/x.ics", "Mars/Olympus"))
                .isInstanceOfSatisfying(UserFacingException.class, e -> {
                    assertThat(e.code()).isEqualTo(CalendarErrorCode.INVALID_CALENDAR);
                    assertThat(e.args()).containsEntry("fields", "address,zone");
                });
        assertThat(setup.connect(BAND, ADDRESS, " ").zone()).as("blank: the default").isEqualTo("Europe/Prague");
        assertThatThrownBy(() -> setup.connect(BAND, "https://10.0.0.1/ical/x.ics", null))
                .as("never the server's own network").isInstanceOfSatisfying(UserFacingException.class,
                        e -> assertThat(e.args()).containsEntry("fields", "address"));
    }

    @Test
    void disconnecting_forgets_the_address() {
        setup.connect(BAND, ADDRESS, null);
        setup.disconnect(BAND);
        assertThat(calendars.of(BAND)).isEmpty();
    }

    @Test
    void the_bands_own_rules_are_replaced_whole_and_one_that_isnt_a_rule_changes_nothing() {
        profiles.replaceShipped(List.of(ProfileRule.preset(RuleKind.REPEATING, null, -10)));

        List<ProfileRule> now = setup.replaceOwnRules(BAND, List.of(
                new RuleDraft("title_starts_with", "skuska|reh", -6, true),
                new RuleDraft("LONGER_THAN_DAYS", "3", -3, false)));

        assertThat(now).hasSize(3);
        assertThat(profiles.rules(BAND)).filteredOn(r -> r.origin() == RuleOrigin.USER).containsExactly(
                new ProfileRule(RuleKind.TITLE_STARTS_WITH, "skuska|reh", -6, RuleOrigin.USER, true),
                new ProfileRule(RuleKind.LONGER_THAN_DAYS, "3", -3, RuleOrigin.USER, false));
        assertThat(profiles.rules(OTHER)).hasSize(1);

        assertThatThrownBy(() -> setup.replaceOwnRules(BAND, List.of(new RuleDraft("TITLE_CONTAINS", "x", 1, true),
                new RuleDraft("LONGER_THAN_DAYS", "three", -3, true))))
                .isInstanceOfSatisfying(UserFacingException.class, e -> {
                    assertThat(e.code()).isEqualTo(CalendarErrorCode.INVALID_RULE);
                    assertThat(e.args()).containsEntry("rule", "2").containsKey("reason");
                });
        assertThatThrownBy(() -> setup.replaceOwnRules(BAND, List.of(new RuleDraft("NO_SUCH", null, 1, true))))
                .isInstanceOfSatisfying(UserFacingException.class,
                        e -> assertThat(e.args()).containsEntry("reason", "no rule kind NO_SUCH"));
        assertThat(profiles.rules(BAND)).filteredOn(r -> r.origin() == RuleOrigin.USER).hasSize(2);
    }

    @Test
    void the_configurations_calendar_is_kept_once() {
        assertThat(setup.adopt(BAND, ADDRESS, ZoneId.of("Europe/Prague"))).isTrue();
        assertThat(setup.adopt(BAND, "https://elsewhere.test/x.ics", ZoneId.of("Europe/Prague"))).isFalse();
        assertThat(calendars.of(BAND).orElseThrow().host()).isEqualTo("1.1.1.1");
        assertThat(live.updates).isEmpty();
    }
}
