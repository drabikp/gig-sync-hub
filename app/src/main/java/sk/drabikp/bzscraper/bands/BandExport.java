package sk.drabikp.bzscraper.bands;

import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.accounts.application.port.in.AccountsErrorCode;
import sk.drabikp.bzscraper.accounts.application.port.in.AuditUseCase;
import sk.drabikp.bzscraper.accounts.application.port.in.BandsUseCase;
import sk.drabikp.bzscraper.accounts.application.port.in.BandsUseCase.MyBand;
import sk.drabikp.bzscraper.accounts.application.port.in.MembersUseCase;
import sk.drabikp.bzscraper.accounts.application.port.in.PlatformAccountsUseCase;
import sk.drabikp.bzscraper.accounts.domain.UserId;
import sk.drabikp.bzscraper.calendar.application.port.in.CalendarSetupUseCase;
import sk.drabikp.bzscraper.calendar.application.port.in.ReviewCalendarUseCase;
import sk.drabikp.bzscraper.calendar.domain.CalendarRow;
import sk.drabikp.bzscraper.calendar.domain.rules.RuleOrigin;
import sk.drabikp.bzscraper.catalog.application.port.in.ListGigsUseCase;
import sk.drabikp.bzscraper.catalog.application.port.in.ListPublicationsUseCase;
import sk.drabikp.bzscraper.gig.application.UserFacingException;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.Gig;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A band's data as one file, for its owners (to keep, or to take elsewhere): the band, its members, its gigs
 * and their platform copies, the calendar's verdicts and links (titles and days — never the events' notes),
 * its own calendar rules, its platform accounts' settings (never a secret), the audit log. Gathered from the
 * features' ports; spans features, so it is the app's.
 */
@Component
class BandExport {

    /** The file's name and what it holds. */
    record Export(String fileName, Map<String, Object> data) {
    }

    private final BandsUseCase bands;
    private final MembersUseCase members;
    private final ListGigsUseCase gigs;
    private final ListPublicationsUseCase publications;
    private final ReviewCalendarUseCase calendar;
    private final CalendarSetupUseCase calendarSetup;
    private final PlatformAccountsUseCase accounts;
    private final AuditUseCase audit;
    private final Clock clock;

    BandExport(BandsUseCase bands, MembersUseCase members, ListGigsUseCase gigs, ListPublicationsUseCase publications,
               ReviewCalendarUseCase calendar, CalendarSetupUseCase calendarSetup, PlatformAccountsUseCase accounts,
               AuditUseCase audit, Clock clock) {
        this.bands = bands;
        this.members = members;
        this.gigs = gigs;
        this.publications = publications;
        this.calendar = calendar;
        this.calendarSetup = calendarSetup;
        this.accounts = accounts;
        this.audit = audit;
        this.clock = clock;
    }

    Export of(UserId owner, BandId band) {
        MyBand mine = bands.band(owner, band);
        if (!mine.role().mayChangeTheBand()) {
            throw new UserFacingException(AccountsErrorCode.OWNER_ONLY, Map.of(), "Only the band's owners may export it.");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("exportedAt", clock.instant().toString());
        data.put("band", Map.of("id", band.toString(), "slug", mine.band().slug(), "name", mine.band().name()));
        data.put("members", members.members(owner, band).stream().map(m -> {
            Map<String, Object> member = new LinkedHashMap<>();
            member.put("name", m.user().shownAs());
            member.put("email", m.user().email());
            member.put("role", m.role().name());
            return member;
        }).toList());
        List<Gig> all = gigs.allGigs(band);
        data.put("gigs", all);
        data.put("publications", publications.publicationsByGig(band).values().stream()
                .flatMap(byPlatform -> byPlatform.values().stream())
                .map(p -> Map.of("gig", p.gigId().key(), "platform", p.platform().id(),
                        "externalRef", p.externalRef() == null ? "" : p.externalRef()))
                .toList());
        Map<String, Object> calendarData = new LinkedHashMap<>();
        calendarData.put("connection", calendarSetup.connection(band));
        calendarData.put("ownRules", calendar.rules(band).stream().filter(r -> r.origin() != RuleOrigin.PRESET).toList());
        calendarData.put("events", calendar.configured(band) ? events(calendar.overview(band).rows()) : List.of());
        data.put("calendar", calendarData);
        data.put("platformAccounts", accounts.accounts(owner, band).stream().map(a -> Map.of(
                "platform", a.platform().id(), "connected", a.connected(), "settings", a.settings())).toList());
        data.put("audit", audit.recent(owner, band, 500).stream().map(e -> {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("at", e.event().at().toString());
            event.put("who", e.who());
            event.put("action", e.event().action());
            event.put("target", e.event().target());
            event.put("status", e.event().status());
            return event;
        }).toList());
        return new Export(mine.band().slug() + "-export.json", data);
    }

    /** The calendar's events as the band sorted them: the day, the title, the verdict, the linked gig. */
    private static List<Map<String, Object>> events(List<CalendarRow> rows) {
        List<Map<String, Object>> events = new ArrayList<>();
        for (CalendarRow row : rows) {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("id", row.eventId());
            event.put("title", row.event().title());
            event.put("start", row.event().start().toString());
            event.put("kind", row.classification().kind().name());
            event.put("decidedByUser", row.classification().decidedByUser());
            event.put("linkedGig", row.match().state().name().equals("LINKED") && row.match().gig() != null
                    ? row.match().gig().id().key() : null);
            events.add(event);
        }
        return events;
    }
}
