package sk.drabikp.gigsynchub.calendar.application;

import org.junit.jupiter.api.Test;
import sk.drabikp.gigsynchub.accounts.application.port.in.AccountsErrorCode;
import sk.drabikp.gigsynchub.accounts.application.port.in.BandAccess;
import sk.drabikp.gigsynchub.accounts.domain.Role;
import sk.drabikp.gigsynchub.accounts.domain.UserId;
import sk.drabikp.gigsynchub.calendar.application.port.in.CalendarErrorCode;
import sk.drabikp.gigsynchub.calendar.application.port.in.CalendarSetupUseCase.Connection;
import sk.drabikp.gigsynchub.calendar.application.port.in.CalendarSetupUseCase.RuleDraft;
import sk.drabikp.gigsynchub.calendar.domain.rules.ProfileRule;
import sk.drabikp.gigsynchub.calendar.domain.rules.RuleKind;
import sk.drabikp.gigsynchub.calendar.domain.rules.RuleOrigin;
import sk.drabikp.gigsynchub.gig.application.UserFacingException;
import sk.drabikp.gigsynchub.gig.application.port.out.LiveUpdates;
import sk.drabikp.gigsynchub.gig.application.port.out.TestLiveUpdates;

import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static sk.drabikp.gigsynchub.gig.domain.TestBands.BAND;
import static sk.drabikp.gigsynchub.gig.domain.TestBands.OTHER;

class CalendarSetupServiceTest {

    private static final String ADDRESS = "webcal://1.1.1.1/ical/secret-part/basic.ics";

    private final CalendarFakes.Calendars calendars = new CalendarFakes.Calendars();
    private final CalendarFakes.Profiles profiles = new CalendarFakes.Profiles();
    private final TestLiveUpdates live = new TestLiveUpdates();
    private static final UserId OWNER = new UserId(UUID.randomUUID());
    private static final UserId MANAGER = new UserId(UUID.randomUUID());

    /** Who is what in which band, as the accounts would answer. */
    private final BandAccess access = (user, band) -> {
        if (!band.equals(BAND)) {
            return Optional.empty();
        }
        return user.equals(OWNER) ? Optional.of(Role.OWNER)
                : user.equals(MANAGER) ? Optional.of(Role.MANAGER) : Optional.empty();
    };
    private final CalendarSetupService setup = new CalendarSetupService(calendars, profiles, live, access,
            ZoneId.of("Europe/Prague"));

    @Test
    void a_band_connects_its_calendar_and_only_whose_it_is_is_shown_again() {
        assertThat(setup.connection(BAND)).isEqualTo(new Connection(false, null, "Europe/Prague"));

        Connection connected = setup.connect(OWNER, BAND, ADDRESS, "Europe/Bratislava");

        assertThat(connected).isEqualTo(new Connection(true, "1.1.1.1", "Europe/Bratislava"));
        assertThat(connected.toString()).doesNotContain("secret-part");
        assertThat(calendars.of(BAND).orElseThrow().address()).startsWith("https://").contains("secret-part");
        assertThat(calendars.of(BAND).orElseThrow().toString()).doesNotContain("secret-part");
        assertThat(setup.connection(OTHER).connected()).isFalse();
        assertThat(live.updates).containsExactly(new TestLiveUpdates.Update(BAND, LiveUpdates.Topic.CALENDAR));
    }

    @Test
    void an_address_or_zone_that_isnt_one_is_refused() {
        assertThatThrownBy(() -> setup.connect(OWNER, BAND, "1.1.1.1/x.ics", "Mars/Olympus"))
                .isInstanceOfSatisfying(UserFacingException.class, e -> {
                    assertThat(e.code()).isEqualTo(CalendarErrorCode.INVALID_CALENDAR);
                    assertThat(e.args()).containsEntry("fields", "address,zone");
                });
        assertThat(setup.connect(OWNER, BAND, ADDRESS, " ").zone()).as("blank: the default").isEqualTo("Europe/Prague");
        assertThatThrownBy(() -> setup.connect(OWNER, BAND, "https://10.0.0.1/ical/x.ics", null))
                .as("never the server's own network").isInstanceOfSatisfying(UserFacingException.class,
                        e -> assertThat(e.args()).containsEntry("fields", "address"));
    }

    @Test
    void disconnecting_forgets_the_address() {
        setup.connect(OWNER, BAND, ADDRESS, null);
        setup.disconnect(OWNER, BAND);
        assertThat(calendars.of(BAND)).isEmpty();
    }

    /**
     * The band's calendar is its owners' — the same line the platform accounts draw, for the same
     * reason: both hand the server a credential the band cannot get back out of it. A manager could
     * otherwise point the calendar at a feed of their own and have the catalog publish gigs of their
     * choosing under the band's name, or destroy the owner's address with one DELETE.
     */
    @Test
    void only_an_owner_connects_or_disconnects_the_calendar() {
        assertThatThrownBy(() -> setup.connect(MANAGER, BAND, ADDRESS, null))
                .isInstanceOfSatisfying(UserFacingException.class,
                        e -> assertThat(e.code()).isEqualTo(AccountsErrorCode.OWNER_ONLY));

        setup.connect(OWNER, BAND, ADDRESS, null);
        assertThatThrownBy(() -> setup.disconnect(MANAGER, BAND))
                .isInstanceOfSatisfying(UserFacingException.class,
                        e -> assertThat(e.code()).isEqualTo(AccountsErrorCode.OWNER_ONLY));
        assertThat(calendars.of(BAND)).as("and nothing was destroyed on the way").isPresent();

        assertThatThrownBy(() -> setup.connect(OWNER, OTHER, ADDRESS, null))
                .as("a band one is not in has no calendar to connect")
                .isInstanceOf(sk.drabikp.gigsynchub.gig.application.NotFoundException.class);
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
