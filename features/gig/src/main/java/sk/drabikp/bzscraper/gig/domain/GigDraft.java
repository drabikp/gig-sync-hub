package sk.drabikp.bzscraper.gig.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A gig as a form holds it — days and times as the band reads them in the gig's own country,
 * the town's address details when it was picked from the place search — before it is a
 * {@link Gig}. {@link #problems()} names what is missing or doesn't fit; {@link #toGig()} builds
 * the gig.
 */
public record GigDraft(String title, When when, Where where, About about, boolean cancelled) {

    /**
     * When it is.
     *
     * @param endDate     null with an {@code endTime}: the day it starts, or the next one when the
     *                    end is earlier than the start (a night that runs past midnight)
     * @param slotDate    when the band itself plays (a festival over several days); with {@code slotTime}
     * @param slotEndTime the band's set ends; past midnight when earlier than {@code slotTime}
     */
    public record When(LocalDate date, LocalTime time, LocalDate endDate, LocalTime endTime,
                       LocalDate slotDate, LocalTime slotTime, LocalTime slotEndTime) {
    }

    /**
     * Where it is: the town, and the details of the town picked from the place search.
     *
     * @param venue null or blank: not known yet (the gig is then known by its town)
     */
    public record Where(String venue, String city, Country country,
                        String street, String postalCode, String district, String region,
                        Double latitude, Double longitude) {
    }

    /** What it is: who plays, the entry, the texts and links. */
    public record About(List<String> lineup, EntryType entry, String price,
                        String description, String facebookUrl, String ticketUrl, String posterUrl) {

        public About {
            lineup = lineup == null ? List.of()
                    : lineup.stream().filter(b -> b != null && !b.isBlank()).map(String::strip).toList();
            entry = entry == null ? EntryType.FREE : entry;
        }
    }

    /** What is missing or doesn't fit; {@link #field()} is the form's name for it. */
    public enum Problem {
        TITLE("title"), DATE("date"), TIME("time"), CITY("city"), COUNTRY("country"), PRICE("price"),
        SLOT("slot"), END_TIME("endTime"), COORDINATES("coordinates"), DATES("dates"),
        /** A Facebook, ticket or poster link that isn't an http(s) address (shown as a link, sent to the platforms). */
        LINKS("links");

        private final String field;

        Problem(String field) {
            this.field = field;
        }

        public String field() {
            return field;
        }
    }

    public GigDraft {
        when = when == null ? new When(null, null, null, null, null, null, null) : when;
        where = where == null ? new Where(null, null, null, null, null, null, null, null, null) : where;
        about = about == null ? new About(null, null, null, null, null, null, null) : about;
    }

    /** The draft of an existing gig, to edit it. */
    public static GigDraft of(Gig gig) {
        GigSchedule schedule = gig.schedule();
        ZonedDateTime end = schedule.end();
        Slot slot = schedule.slot();
        When when = new When(schedule.start().toLocalDate(), schedule.start().toLocalTime(),
                end == null ? null : end.toLocalDate(), end == null ? null : end.toLocalTime(),
                slot == null ? null : slot.start().toLocalDate(), slot == null ? null : slot.start().toLocalTime(),
                slot == null || slot.end() == null ? null : slot.end().toLocalTime());
        Location location = gig.location();
        Address address = location.address() != null ? location.address() : Address.NONE;
        Where where = new Where(location.venue(), location.city(), location.country(), address.street(),
                address.postalCode(), address.district(), address.region(), address.latitude(), address.longitude());
        About about = new About(gig.lineup(), gig.admission().type(), gig.admission().amount(), gig.description(),
                gig.facebookUrl(), gig.ticketUrl(), gig.posterImageUrl());
        return new GigDraft(gig.title(), when, where, about, gig.cancelled());
    }

    /** What is missing or doesn't fit; empty when it is a gig. */
    public List<Problem> problems() {
        List<Problem> problems = missing();
        if (problems.isEmpty() && !timesFit(times())) {
            problems.add(Problem.DATES);
        }
        return problems;
    }

    /** The gig; IllegalArgumentException when it can't be one (see {@link #problems()}). */
    public Gig toGig() {
        List<Problem> problems = problems();
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("not a gig yet: " + problems);
        }
        Times times = times();
        Slot slot = times.slotStart() == null ? null : new Slot(times.slotStart(), times.slotEnd());
        Address address = Address.ofParts(where.street(), where.postalCode(), where.district(), where.region(),
                where.latitude(), where.longitude());
        Gig gig = Gig.create(title, new GigSchedule(times.start(), times.end(), slot),
                new Location(nullIfBlank(where.venue()), where.city().strip(), where.country(), address),
                about.lineup(), Admission.of(about.entry(), about.price()), nullIfBlank(about.description()),
                nullIfBlank(about.facebookUrl()), nullIfBlank(about.ticketUrl()), nullIfBlank(about.posterUrl()));
        return cancelled ? gig.cancel() : gig;
    }

    private List<Problem> missing() {
        List<Problem> missing = new ArrayList<>();
        if (blank(title)) {
            missing.add(Problem.TITLE);
        }
        if (when.date() == null) {
            missing.add(Problem.DATE);
        }
        if (when.time() == null) {
            missing.add(Problem.TIME);
        }
        if (blank(where.city())) {
            missing.add(Problem.CITY);
        }
        if (where.country() == null) {
            missing.add(Problem.COUNTRY);
        }
        if (about.entry() == EntryType.PAID && blank(about.price())) {
            missing.add(Problem.PRICE);
        }
        if ((when.slotDate() == null) != (when.slotTime() == null)
                || when.slotDate() == null && when.slotEndTime() != null) {
            missing.add(Problem.SLOT);
        }
        if (when.endDate() != null && when.endTime() == null) {
            missing.add(Problem.END_TIME);
        }
        if ((where.latitude() == null) != (where.longitude() == null)) {
            missing.add(Problem.COORDINATES);
        }
        if (!webLink(about.facebookUrl()) || !webLink(about.ticketUrl()) || !webLink(about.posterUrl())) {
            missing.add(Problem.LINKS);
        }
        return missing;
    }

    /** Blank, or an http(s) address — never {@code javascript:} or the like. */
    private static boolean webLink(String link) {
        return blank(link) || link.strip().matches("(?i)https?://\\S+");
    }

    /** The event's and the band's times in the gig's country (call only when nothing is {@link #missing()}). */
    private Times times() {
        ZoneId zone = ZoneId.of(where.country().timezone());
        ZonedDateTime start = ZonedDateTime.of(when.date(), when.time(), zone);
        ZonedDateTime end = null;
        if (when.endTime() != null) {
            end = ZonedDateTime.of(when.endDate() != null ? when.endDate() : when.date(), when.endTime(), zone);
            if (when.endDate() == null && !end.isAfter(start)) {
                end = end.plusDays(1);
            }
        }
        ZonedDateTime slotStart = null;
        ZonedDateTime slotEnd = null;
        if (when.slotDate() != null) {
            slotStart = ZonedDateTime.of(when.slotDate(), when.slotTime(), zone);
            if (when.slotEndTime() != null) {
                slotEnd = ZonedDateTime.of(when.slotDate(), when.slotEndTime(), zone);
                if (slotEnd.isBefore(slotStart)) {
                    slotEnd = slotEnd.plusDays(1);
                }
            }
        }
        return new Times(start, end, slotStart, slotEnd);
    }

    /** The rules of {@link GigSchedule}: the end not before the start, the band's slot within the event. */
    private static boolean timesFit(Times times) {
        if (times.end() != null && times.end().isBefore(times.start())) {
            return false;
        }
        return times.slotStart() == null || !times.slotStart().isBefore(times.start())
                && (times.end() == null || !times.slotStart().isAfter(times.end()));
    }

    private record Times(ZonedDateTime start, ZonedDateTime end, ZonedDateTime slotStart, ZonedDateTime slotEnd) {
    }

    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }

    private static String nullIfBlank(String text) {
        return blank(text) ? null : text.strip();
    }
}
