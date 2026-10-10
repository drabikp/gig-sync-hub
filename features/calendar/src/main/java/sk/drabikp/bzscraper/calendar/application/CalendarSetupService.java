package sk.drabikp.bzscraper.calendar.application;

import sk.drabikp.bzscraper.calendar.application.port.in.CalendarErrorCode;
import sk.drabikp.bzscraper.calendar.application.port.in.CalendarSetupUseCase;
import sk.drabikp.bzscraper.calendar.application.port.out.BandCalendarStore;
import sk.drabikp.bzscraper.calendar.application.port.out.BandProfileStore;
import sk.drabikp.bzscraper.calendar.domain.BandCalendar;
import sk.drabikp.bzscraper.calendar.domain.rules.ProfileRule;
import sk.drabikp.bzscraper.calendar.domain.rules.RuleKind;
import sk.drabikp.bzscraper.calendar.domain.rules.RuleOrigin;
import sk.drabikp.bzscraper.gig.application.PublicAddresses;
import sk.drabikp.bzscraper.gig.application.UserFacingException;
import sk.drabikp.bzscraper.gig.application.port.out.LiveUpdates;
import sk.drabikp.bzscraper.gig.domain.BandId;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Which calendar a band reads and the band's own rules. The calendar's pages are told of every change
 * (the live updates: calendar). A band's first calendar and rules may also come from the server's
 * configuration — band #1's, from before each band set up its own ({@link #adopt}).
 */
public class CalendarSetupService implements CalendarSetupUseCase {

    private final BandCalendarStore calendars;
    private final BandProfileStore profiles;
    private final LiveUpdates live;
    private final ZoneId defaultZone;

    public CalendarSetupService(BandCalendarStore calendars, BandProfileStore profiles, LiveUpdates live,
                                ZoneId defaultZone) {
        this.calendars = calendars;
        this.profiles = profiles;
        this.live = live;
        this.defaultZone = defaultZone;
    }

    @Override
    public Connection connection(BandId band) {
        return calendars.of(band).map(c -> new Connection(true, c.host(), c.zone().getId()))
                .orElseGet(() -> new Connection(false, null, defaultZone.getId()));
    }

    @Override
    public Connection connect(BandId band, String address, String zone) {
        List<String> wrong = new ArrayList<>();
        if (!BandCalendar.validAddress(address)
                || !PublicAddresses.fetchable(address.strip().replaceFirst("(?i)^webcal://", "https://"))) {
            wrong.add("address");                         // not one, or not on the public internet
        }
        ZoneId zoneId = defaultZone;
        if (zone != null && !zone.isBlank()) {
            try {
                zoneId = ZoneId.of(zone.strip());
            } catch (DateTimeException e) {
                wrong.add("zone");
            }
        }
        if (!wrong.isEmpty()) {
            throw new UserFacingException(CalendarErrorCode.INVALID_CALENDAR, Map.of("fields", String.join(",", wrong)),
                    "Check the calendar's " + String.join(" and ", wrong) + ".");
        }
        calendars.save(new BandCalendar(band, address, zoneId));
        live.changed(band, LiveUpdates.Topic.CALENDAR);
        return connection(band);
    }

    @Override
    public void disconnect(BandId band) {
        calendars.delete(band);
        live.changed(band, LiveUpdates.Topic.CALENDAR);
    }

    @Override
    public List<RuleKind> ruleKinds() {
        return List.of(RuleKind.values());
    }

    @Override
    public List<ProfileRule> replaceOwnRules(BandId band, List<RuleDraft> drafts) {
        List<ProfileRule> own = new ArrayList<>();
        for (int i = 0; i < drafts.size(); i++) {
            own.add(rule(i + 1, drafts.get(i)));
        }
        profiles.replace(band, RuleOrigin.USER, own);
        live.changed(band, LiveUpdates.Topic.CALENDAR);
        return profiles.rules(band);
    }

    /**
     * The calendar the configuration had for the band (band #1's), kept once — when the band has none yet.
     *
     * @return true when it was kept
     */
    public boolean adopt(BandId band, String address, ZoneId zone) {
        if (calendars.of(band).isPresent() || !BandCalendar.validAddress(address)) {
            return false;
        }
        calendars.save(new BandCalendar(band, address, zone));
        return true;
    }

    /** Seals again every calendar address an older key sealed; how many. */
    public int reseal() {
        return calendars.reseal();
    }

    private static ProfileRule rule(int number, RuleDraft draft) {
        try {
            RuleKind kind = RuleKind.valueOf(String.valueOf(draft.kind()).strip().toUpperCase(Locale.ROOT));
            return new ProfileRule(kind, draft.value(), draft.weight(), RuleOrigin.USER, draft.enabled());
        } catch (IllegalArgumentException e) {
            String reason = e.getMessage() != null && e.getMessage().startsWith("No enum constant")
                    ? "no rule kind " + draft.kind() : e.getMessage();
            throw new UserFacingException(CalendarErrorCode.INVALID_RULE,
                    Map.of("rule", String.valueOf(number), "reason", reason), "Rule " + number + ": " + reason);
        }
    }
}
