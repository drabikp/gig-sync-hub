package sk.drabikp.gigsynchub.calendar.adapter.out.ics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import sk.drabikp.gigsynchub.calendar.application.port.out.BandCalendarStore;
import sk.drabikp.gigsynchub.calendar.application.port.out.CalendarFeed;
import sk.drabikp.gigsynchub.calendar.application.port.out.CalendarUnavailableException;
import sk.drabikp.gigsynchub.calendar.domain.BandCalendar;
import sk.drabikp.gigsynchub.calendar.domain.event.CalendarEvent;
import sk.drabikp.gigsynchub.gig.application.PublicAddresses;
import sk.drabikp.gigsynchub.gig.domain.BandId;

import java.io.IOException;
import java.io.InputStream;
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
 * calendar, so it is never logged or shown. Only a public address is read (not the server's own network:
 * {@link PublicAddresses}), redirects too.
 */
@Component
class IcsCalendarFeed implements CalendarFeed {

    private static final Logger log = LoggerFactory.getLogger(IcsCalendarFeed.class);

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_REDIRECTS = 5;

    private final BandCalendarStore calendars;
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)            // followed by hand, each one checked
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
        HttpResponse<InputStream> response;
        String body;
        try {
            URI address = URI.create(calendar.address());
            response = http.send(get(address), HttpResponse.BodyHandlers.ofInputStream());
            for (int hops = 0; hops < MAX_REDIRECTS && isRedirect(response.statusCode()); hops++) {
                response.body().close();
                address = address.resolve(response.headers().firstValue("Location").orElseThrow(() ->
                        new IllegalArgumentException("a redirect without its address")));
                response = http.send(get(address), HttpResponse.BodyHandlers.ofInputStream());
            }
            if (response.statusCode() != 200) {
                response.body().close();
                // no status code to the band: it would tell what answers where
                throw new CalendarUnavailableException("The calendar couldn't be read — is its address still valid?");
            }
            body = new String(PublicAddresses.limited(response.body()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new CalendarUnavailableException("The calendar could not be reached.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CalendarUnavailableException("Reading the calendar was interrupted", e);
        } catch (IllegalArgumentException e) {
            // Deliberately WITHOUT e.getMessage(). PublicAddresses says either "the host X is
            // unknown" or "the host X isn't on the public internet", and handing that back told
            // whoever typed the address which internal names exist — a private-DNS oracle anyone
            // with a band could read one name at a time. The reason is in the log, where the
            // operator can see it and a band cannot.
            log.info("A band's calendar address was refused", e);
            throw new CalendarUnavailableException("The calendar address can't be read.", e);
        }
        if (!body.contains("BEGIN:VCALENDAR")) {
            throw new CalendarUnavailableException("The calendar address did not return an iCal calendar");
        }
        return IcsParser.parse(body, calendar.zone());
    }

    /** A request to a public address only ({@link PublicAddresses}) — never the server's own network. */
    private static HttpRequest get(URI address) {
        return HttpRequest.newBuilder(PublicAddresses.checked(address)).timeout(TIMEOUT).GET().build();
    }

    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }
}
