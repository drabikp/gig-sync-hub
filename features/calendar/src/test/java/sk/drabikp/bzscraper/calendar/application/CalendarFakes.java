package sk.drabikp.bzscraper.calendar.application;

import sk.drabikp.bzscraper.calendar.application.port.out.BandCalendarStore;
import sk.drabikp.bzscraper.calendar.application.port.out.BandProfileStore;
import sk.drabikp.bzscraper.calendar.application.port.out.CalendarDecisionStore;
import sk.drabikp.bzscraper.calendar.application.port.out.CalendarLinkStore;
import sk.drabikp.bzscraper.calendar.application.port.out.CalendarSnapshotStore;
import sk.drabikp.bzscraper.calendar.domain.BandCalendar;
import sk.drabikp.bzscraper.calendar.domain.KnownCalendarEvent;
import sk.drabikp.bzscraper.calendar.domain.event.CalendarEventKind;
import sk.drabikp.bzscraper.calendar.domain.rules.ProfileRule;
import sk.drabikp.bzscraper.calendar.domain.rules.RuleOrigin;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.GigId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** In-memory calendar stores for service tests, each band's apart. */
final class CalendarFakes {

    private CalendarFakes() {
    }

    /** Where each band's calendar is read from. */
    static final class Calendars implements BandCalendarStore {

        final Map<BandId, BandCalendar> calendars = new HashMap<>();

        @Override
        public Optional<BandCalendar> of(BandId band) {
            return Optional.ofNullable(calendars.get(band));
        }

        @Override
        public void save(BandCalendar calendar) {
            calendars.put(calendar.band(), calendar);
        }

        @Override
        public void delete(BandId band) {
            calendars.remove(band);
        }

        @Override
        public int reseal() {
            return 0;
        }
    }

    /** The shipped rules (everyone's) and each band's own, replaced per origin. */
    static final class Profiles implements BandProfileStore {

        final List<ProfileRule> shipped = new ArrayList<>();
        final Map<BandId, List<ProfileRule>> own = new HashMap<>();

        @Override
        public List<ProfileRule> rules(BandId band) {
            List<ProfileRule> rules = new ArrayList<>(shipped);
            rules.addAll(own.getOrDefault(band, List.of()));
            return rules;
        }

        @Override
        public void replaceShipped(List<ProfileRule> rules) {
            shipped.clear();
            shipped.addAll(rules);
        }

        @Override
        public void replace(BandId band, RuleOrigin origin, List<ProfileRule> rules) {
            List<ProfileRule> theirs = own.computeIfAbsent(band, b -> new ArrayList<>());
            theirs.removeIf(r -> r.origin() == origin);
            theirs.addAll(rules);
        }
    }

    static final class Links implements CalendarLinkStore {

        final Map<BandId, Map<String, GigId>> links = new HashMap<>();

        Map<String, GigId> of(BandId band) {
            return links.computeIfAbsent(band, b -> new HashMap<>());
        }

        @Override
        public Map<String, GigId> all(BandId band) {
            return Map.copyOf(of(band));
        }

        @Override
        public void link(BandId band, String eventId, GigId gigId) {
            of(band).put(eventId, gigId);
        }

        @Override
        public void unlink(BandId band, String eventId) {
            of(band).remove(eventId);
        }

        @Override
        public void move(BandId band, GigId from, GigId to) {
            of(band).replaceAll((event, gig) -> gig.equals(from) ? to : gig);
        }
    }

    static final class Snapshots implements CalendarSnapshotStore {

        final Map<BandId, Map<String, KnownCalendarEvent>> events = new HashMap<>();
        final Map<BandId, Instant> lastRead = new HashMap<>();

        Map<String, KnownCalendarEvent> of(BandId band) {
            return events.computeIfAbsent(band, b -> new LinkedHashMap<>());
        }

        @Override
        public Map<String, KnownCalendarEvent> all(BandId band) {
            return new LinkedHashMap<>(of(band));
        }

        @Override
        public Optional<KnownCalendarEvent> find(BandId band, String eventId) {
            return Optional.ofNullable(of(band).get(eventId));
        }

        @Override
        public void saveRead(BandId band, Collection<KnownCalendarEvent> read, Instant readAt) {
            read.forEach(event -> save(band, event));
            lastRead.put(band, readAt);
        }

        @Override
        public void save(BandId band, KnownCalendarEvent event) {
            of(band).put(event.id(), event);
        }

        @Override
        public void remove(BandId band, String eventId) {
            of(band).remove(eventId);
        }

        @Override
        public Optional<Instant> lastRead(BandId band) {
            return Optional.ofNullable(lastRead.get(band));
        }
    }

    static final class Decisions implements CalendarDecisionStore {

        final Map<BandId, Map<String, CalendarEventKind>> verdicts = new HashMap<>();

        Map<String, CalendarEventKind> of(BandId band) {
            return verdicts.computeIfAbsent(band, b -> new HashMap<>());
        }

        @Override
        public Map<String, CalendarEventKind> all(BandId band) {
            return Map.copyOf(of(band));
        }

        @Override
        public void decide(BandId band, String eventId, CalendarEventKind verdict) {
            of(band).put(eventId, verdict);
        }

        @Override
        public void forget(BandId band, String eventId) {
            of(band).remove(eventId);
        }
    }
}
