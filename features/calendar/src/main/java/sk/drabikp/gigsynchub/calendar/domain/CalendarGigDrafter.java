package sk.drabikp.gigsynchub.calendar.domain;

import org.jmolecules.ddd.annotation.Service;
import sk.drabikp.gigsynchub.calendar.domain.event.CalendarEvent;
import sk.drabikp.gigsynchub.calendar.domain.rules.BandProfile;
import sk.drabikp.gigsynchub.calendar.domain.rules.NotesLabels;
import sk.drabikp.gigsynchub.calendar.domain.rules.ProfileRule;
import sk.drabikp.gigsynchub.calendar.domain.rules.RuleKind;
import sk.drabikp.gigsynchub.gig.domain.CityName;
import sk.drabikp.gigsynchub.gig.domain.Country;
import sk.drabikp.gigsynchub.gig.domain.TextFold;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Pure domain service: reads what a calendar event says about its gig
 * ({@link CalendarGigDraft}). Private notes are never copied — only the show time is read
 * from them.
 * <ul>
 *   <li><b>Show time</b>: the first {@link RuleKind#SHOWTIME_LABEL} label in the notes
 *       followed by a time ("Showtime: 19:30", "- show: 21.00"). A show time before 06:00
 *       on an event that starts later is after midnight: the show is on the next day.</li>
 *   <li><b>Place</b> as map apps write it — {@code Venue, Street 1, 811 05 City-District,
 *       Country}: the city follows the postal code (district and district number dropped),
 *       the venue is the first part. Without a postal code: {@code Venue, City} or just
 *       {@code City}. Coordinates, plus codes and map links say nothing.</li>
 * </ul>
 */
@Service
public final class CalendarGigDrafter {

    private static final LocalTime EARLY_MORNING = LocalTime.of(6, 0);
    // Two \\s* either side of an optional letter is two ways to match the same spaces, so a near
    // miss is retried every way round before it is given up on; possessive (*+) says there is only
    // one way and means it. The text these read is a band's own calendar entry, which this server
    // fetches, so "a pathological line" is somebody else's to write.
    private static final Pattern COORDINATES = Pattern.compile(
            "-?\\d{1,3}\\.\\d++\\s*+[NSns]?\\s*+,\\s*+-?\\d{1,3}\\.\\d++\\s*+[EWew]?");
    private static final Pattern PLUS_CODE =
            Pattern.compile("(?i)[23456789CFGHJMPQRVWX]{4,8}+\\+[23456789CFGHJMPQRVWX]{2,3}+");
    /**
     * A street with a house number in it: a letter somewhere and a digit somewhere. Two passes over
     * a short string, where {@code matches(".*\\p{L}.*\\d.*")} is three greedy {@code .*} taking
     * turns giving characters back to each other.
     */
    private static boolean looksLikeAnAddress(String part) {
        boolean letter = false;
        boolean digit = false;
        for (int i = 0; i < part.length(); i++) {
            letter |= Character.isLetter(part.charAt(i));
            digit |= Character.isDigit(part.charAt(i));
        }
        return letter && digit;
    }

    private static final Pattern POSTAL_CODE_CITY = Pattern.compile("(\\d{3})\\s?(\\d{2})\\s+(\\D.*)");
    /**
     * Postal areas whose map addresses name only the city district ("040 01 Staré Mesto"):
     * the first three digits of the postal code → the city.
     */
    private static final Map<String, String> CITY_OF_DISTRICTS = Map.of("040", "Košice");
    private static final Pattern DISTRICT_NUMBER = Pattern.compile("\\s+(\\d+|[IVX]+)$");
    private static final Pattern LEADING_NUMBER = Pattern.compile("^\\d+\\s+");
    private static final Pattern LINK = Pattern.compile("(?i)\\s*(https?://|www\\.)\\S*\\s*");
    /** Towns whose own name has a hyphen; elsewhere "City-Part" is a city district. */
    private static final Set<String> HYPHENATED = Set.of("frydek-mistek", "brandys nad labem-stara boleslav",
            "sedlec-prcice", "usti nad labem-mesto");

    private CalendarGigDrafter() {
    }

    public static CalendarGigDraft draft(CalendarEvent event, BandProfile profile) {
        LocalTime showTime = showTime(event.notes(), profile).orElse(null);
        LocalDate date = event.day();
        if (showTime != null && !event.allDay() && showTime.isBefore(EARLY_MORNING)
                && showTime.isBefore(event.start().toLocalTime())) {
            date = date.plusDays(1);
        }
        Place place = place(event.location());
        return new CalendarGigDraft(event.id(), event.title(), date, showTime, place.venue(), place.city(),
                place.country(), place.street(), place.postalCode(),
                event.allDay() ? null : event.start().toLocalTime());
    }

    static Optional<LocalTime> showTime(String notes, BandProfile profile) {
        String folded = TextFold.fold(notes);
        for (ProfileRule rule : profile.enabled(RuleKind.SHOWTIME_LABEL)) {
            for (String label : rule.alternatives()) {
                Matcher m = NotesLabels.time(label).matcher(folded);
                while (m.find()) {
                    int hour = Integer.parseInt(m.group(1));
                    int minute = Integer.parseInt(m.group(2));
                    if (hour < 24 && minute < 60) {
                        return Optional.of(LocalTime.of(hour, minute));
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** Where an event is, as far as its place says; parts it doesn't say are null. */
    record Place(String venue, String city, Country country, String street, String postalCode) {
        static final Place NOWHERE = new Place(null, null, null, null, null);

        Place(String venue, String city, Country country) {
            this(venue, city, country, null, null);
        }
    }

    static Place place(String location) {
        if (location == null || location.isBlank() || COORDINATES.matcher(location.strip()).matches()
                || LINK.matcher(location).matches()) {
            return Place.NOWHERE;
        }
        List<String> parts = new ArrayList<>(Arrays.stream(location.split("[,\\n]"))
                .map(String::strip)
                .filter(p -> !p.isEmpty() && !PLUS_CODE.matcher(p).matches())
                .filter(p -> !TextFold.fold(p).startsWith("okres ") && !TextFold.fold(p).startsWith("district "))
                .toList());
        Country country = null;
        if (!parts.isEmpty()) {
            Optional<Country> named = Country.named(parts.getLast());
            if (named.isPresent()) {
                country = named.get();
                parts.removeLast();
            }
        }
        if (parts.isEmpty()) {
            return new Place(null, null, country, null, null);
        }
        int cityAt = -1;
        String city = null;
        String postalCode = null;
        for (int i = parts.size() - 1; i >= 0 && city == null; i--) {
            Matcher postal = POSTAL_CODE_CITY.matcher(parts.get(i));
            if (postal.matches()) {
                cityAt = i;
                postalCode = postal.group(1) + " " + postal.group(2);
                city = CITY_OF_DISTRICTS.getOrDefault(postal.group(1), cleanCity(postal.group(3)));
            }
        }
        if (city == null) {
            cityAt = parts.size() - 1;
            city = cleanCity(parts.getLast());
        }
        String venue = null;
        if (cityAt > 0) {
            String first = parts.getFirst();
            boolean isCity = CityName.same(first, city) || TextFold.fold(first).equals(TextFold.fold(city));
            if (!isCity && !Character.isDigit(first.charAt(0))) {
                venue = first;
            }
        }
        // the part before the postal code, when it has a house number: "Hlavná 12"
        String street = postalCode != null && cityAt > 1 && looksLikeAnAddress(parts.get(cityAt - 1))
                ? parts.get(cityAt - 1) : null;
        return new Place(venue, city.isEmpty() ? null : city, country, street, postalCode);
    }

    private static String cleanCity(String raw) {
        String city = raw.strip();
        int dash = city.indexOf('-');
        if (dash > 0 && !HYPHENATED.contains(TextFold.fold(DISTRICT_NUMBER.matcher(city).replaceFirst("")))) {
            city = city.substring(0, dash);
        }
        city = LEADING_NUMBER.matcher(city.strip()).replaceFirst("");
        return DISTRICT_NUMBER.matcher(city.strip()).replaceFirst("").strip();
    }
}
