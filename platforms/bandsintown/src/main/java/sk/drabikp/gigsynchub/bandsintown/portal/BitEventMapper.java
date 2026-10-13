package sk.drabikp.gigsynchub.bandsintown.portal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sk.drabikp.gigsynchub.bandsintown.BandsintownPlatform;
import sk.drabikp.gigsynchub.gig.domain.Admission;
import sk.drabikp.gigsynchub.gig.domain.Country;
import sk.drabikp.gigsynchub.gig.domain.Gig;
import sk.drabikp.gigsynchub.gig.domain.GigId;
import sk.drabikp.gigsynchub.gig.domain.GigSchedule;
import sk.drabikp.gigsynchub.gig.domain.Location;
import sk.drabikp.gigsynchub.importing.domain.ImportedGig;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Maps one event of the artist portal's event list ({@link BitEvent}) to a gig. Bandsintown has no entry fee, so
 * entry is "free" unless something better is known (see the import merge). Deleted
 * events and events without a city or date are left out; one that can't be read is left out
 * with a warning saying why. An unknown time zone is read in the country's.
 */
public final class BitEventMapper {

    private static final Logger logger = LoggerFactory.getLogger(BitEventMapper.class);

    private BitEventMapper() {
    }

    public static Optional<ImportedGig> toImported(BitEvent event) {
        if (event.deleted() || event.draft() || event.venueCity() == null || event.startDate() == null
                || event.id() == null) {
            return Optional.empty();
        }
        try {
            Country country = country(event.venueCountry());
            ZoneId zone = zone(event, country);
            ZonedDateTime start = at(event.startDate(), event.startTime(), zone);
            ZonedDateTime end = event.endDate() == null ? null : at(event.endDate(), event.endTime(), zone);
            if (end != null && !end.isAfter(start)) {
                end = null;
            }
            Gig gig = new Gig(GigId.fresh(), title(event),
                    new GigSchedule(start, end),
                    new Location(venue(event.venueName()), event.venueCity(), country),
                    lineup(event),
                    Admission.free(),
                    event.description(), null, null, null, false);
            boolean placed = event.latitude() != null && event.longitude() != null;
            return Optional.of(new ImportedGig(BandsintownPlatform.PLATFORM, gig, event.id(),
                    placed ? event.latitude() : null, placed ? event.longitude() : null));
        } catch (DateTimeException | IllegalArgumentException e) {
            logger.warn("Bandsintown event {} left out: {}", event.id(), e.getMessage());
            return Optional.empty();
        }
    }

    private static String title(BitEvent event) {
        if (event.title() != null) {
            return event.title();
        }
        return event.headliner() != null ? event.headliner() : "Concert";
    }

    private static String venue(String name) {
        return name == null || name.equalsIgnoreCase("TBA") ? null : name;
    }

    /** The other bands on the bill (the headliner — the band itself — left out). */
    private static List<String> lineup(BitEvent event) {
        String headliner = event.headliner() == null ? "" : event.headliner();
        return event.lineup().stream().filter(name -> !name.equalsIgnoreCase(headliner)).toList();
    }

    private static ZonedDateTime at(String date, String time, ZoneId zone) {
        LocalTime clock = time == null ? LocalTime.MIDNIGHT : LocalTime.parse(time);
        return LocalDate.parse(date).atTime(clock).atZone(zone);
    }

    /** The venue's time zone; the country's (else UTC) when the portal names none or one Java doesn't know. */
    private static ZoneId zone(BitEvent event, Country country) {
        ZoneId countryZone = country != null ? ZoneId.of(country.timezone()) : ZoneId.of("UTC");
        if (event.venueTimezone() == null) {
            return countryZone;
        }
        try {
            return ZoneId.of(event.venueTimezone());
        } catch (DateTimeException _) {
            logger.warn("Bandsintown event {} has an unknown time zone '{}' — read in {}", event.id(),
                    event.venueTimezone(), countryZone);
            return countryZone;
        }
    }

    public static Country country(String name) {
        if (name == null) {
            return null;
        }
        return switch (name.trim().toLowerCase(Locale.ROOT)) {
            case "czechia", "czech republic", "česko", "česká republika" -> Country.CZECHIA;
            case "slovakia", "slovensko" -> Country.SLOVAKIA;
            default -> null;
        };
    }
}
