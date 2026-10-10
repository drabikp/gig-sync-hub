package sk.drabikp.bzscraper.calendar.adapter.out.ics;

import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.calendar.application.port.out.BandCalendarStore;
import sk.drabikp.bzscraper.calendar.application.port.out.CalendarFeed;
import sk.drabikp.bzscraper.calendar.application.port.out.CalendarUnavailableException;
import sk.drabikp.bzscraper.calendar.domain.BandCalendar;
import sk.drabikp.bzscraper.calendar.domain.event.CalendarEvent;
import sk.drabikp.bzscraper.gig.domain.BandId;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * A band's calendar from its private iCal address (Google: calendar settings → "Secret address in iCal
 * format"), as the band set it up ({@link BandCalendarStore}). The address grants read access to the whole
 * calendar, so it is never logged or shown.
 */
@Component
class IcsCalendarFeed implements CalendarFeed {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final BandCalendarStore calendars;
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(TIMEOUT)
            .build();

    public IcsCalendarFeed(BandCalendarStore calendars) {
        this.calendars = calendars;
    }

    @Override
    public boolean configured(BandId band) {
        return calendars.of(band).isPresent();
    }

    @Override
    public List<CalendarEvent> read(BandId band) throws CalendarUnavailableException {
        BandCalendar calendar = calendars.of(band)
                .orElseThrow(() -> new CalendarUnavailableException("No calendar connected for this band"));
        HttpResponse<String> response;
        try {
            response = http.send(HttpRequest.newBuilder(URI.create(calendar.address())).timeout(TIMEOUT).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new CalendarUnavailableException("The calendar could not be reached: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CalendarUnavailableException("Reading the calendar was interrupted", e);
        } catch (IllegalArgumentException e) {
            throw new CalendarUnavailableException("The calendar address is not a valid URL", e);
        }
        if (response.statusCode() != 200) {
            throw new CalendarUnavailableException("The calendar answered HTTP " + response.statusCode()
                    + " — is its address still valid?");
        }
        if (!response.body().contains("BEGIN:VCALENDAR")) {
            throw new CalendarUnavailableException("The calendar address did not return an iCal calendar");
        }
        return IcsParser.parse(response.body(), calendar.zone());
    }
}
