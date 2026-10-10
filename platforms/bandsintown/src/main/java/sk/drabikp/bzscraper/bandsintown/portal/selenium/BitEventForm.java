package sk.drabikp.bzscraper.bandsintown.portal.selenium;

import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import sk.drabikp.bzscraper.bandsintown.BitPortalException;
import sk.drabikp.bzscraper.gig.domain.Gig;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

/**
 * An event's single-page form ({@code /events/<id>?version=single-page}) — it opens for past
 * events too, which the bulk upload and the event list can't change: the place comes from the
 * portal's venue search (Google places), then dates, times, name, description; Save, or
 * Delete (→ {@link BitDeleteDialog}).
 */
final class BitEventForm {

    private static final DateTimeFormatter FORM_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter FORM_TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.US);

    private final BitPortal portal;
    private final String eventId;

    private BitEventForm(BitPortal portal, String eventId) {
        this.portal = portal;
        this.eventId = eventId;
    }

    /** The event's form, or empty when it doesn't open (an event that is gone has none). */
    public static Optional<BitEventForm> open(BitPortal portal, String eventId) {
        portal.open("events/" + eventId + "?version=single-page");
        try {
            portal.until(ExpectedConditions.presenceOfElementLocated(By.name("venue_name")));
        } catch (TimeoutException e) {
            return Optional.empty();
        }
        portal.replies().install();
        portal.pause();
        return Optional.of(new BitEventForm(portal, eventId));
    }

    /**
     * Searches the venue ({@link BitPlaces#query}) and picks the suggestion in the gig's town; none →
     * the user has to set the place. The field's own "Clear value" drops the old place first
     * (typing over it would keep it); the pick is checked in the field.
     */
    public void pickVenue(Gig gig) throws BitPortalException {
        String city = gig.location().city();
        String query = BitPlaces.query(gig);
        int mark = portal.replies().count();
        WebElement clear = (WebElement) portal.js("var e = arguments[0]; for (var i = 0; i < 4 && e.parentElement; i++)"
                + " e = e.parentElement; return e.querySelector(\"button[aria-label='Clear value']\");", venueField());
        if (clear != null) {
            portal.click(clear);
            portal.pause();
        }
        portal.type(venueField(), query);
        // the search answers while typing — the reply to the whole query is the last one
        PortalReply reply = portal.replies().awaitLast(mark, r -> r.urlContains("/venues/autocomplete"), "the venue search",
                Duration.ofMillis(1500));
        BitPlace place = BitPlaces.choose(reply.places(), gig.location().venue(), city);
        if (place == null) {
            throw BitPortalException.needsUser("Bandsintown's venue search (Google places) has nothing for '" + query
                    + "' in " + city + " — set the place there by hand.");
        }
        WebElement option = portal.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector("li[role='option'][value='" + place.placeId().replace("'", "") + "'] button")));
        portal.click(option);
        portal.pause();
        String picked = venueField().getDomProperty("value");
        if (!place.description().equals(picked)) {
            throw BitPortalException.temporary("Bandsintown's venue field didn't take the place ('" + picked
                    + "') — nothing was saved.");
        }
    }

    /** When the band plays: start, and the end when known. */
    public void setSchedule(ZonedDateTime start, ZonedDateTime end) {
        setField(By.name("start_date"), start.format(FORM_DATE));
        setField(By.name("start_time"), start.format(FORM_TIME));
        if (end != null) {
            setField(By.name("end_date"), end.format(FORM_DATE));
            setField(By.name("end_time"), end.format(FORM_TIME));
        }
    }

    public void setTitle(String title) {
        WebElement field = (WebElement) portal.js("var l = Array.from(document.querySelectorAll('label')).find(x =>"
                + " x.innerText.replace('*', '').trim() === 'Event Name'); return l ? document.getElementById(l.htmlFor) : null;");
        if (field != null) {
            setField(field, title);
        }
    }

    public void setDescription(String description) {
        WebElement field = portal.firstVisible(By.cssSelector("textarea[placeholder='Description'], textarea[name='description']"));
        if (field != null) {
            setField(field, description);
        }
    }

    /** Saves the form; the portal's reply to it (refused unless HTTP 200 and OK). */
    public PortalReply save() throws BitPortalException {
        int mark = portal.replies().count();
        portal.pause();
        portal.click(portal.visibleButton("Save"));
        PortalReply reply = portal.replies().await(mark, r -> r.write() && r.urlContains(eventId), "saving the form");
        if (reply.status() != 200 || !reply.ok()) {
            throw BitPortalException.refused("Bandsintown didn't save event " + eventId + " — "
                    + (reply.rowErrors().isEmpty() ? reply.describe() : String.join(", ", reply.rowErrors())));
        }
        return reply;
    }

    /** The form's Delete → the remove dialog. */
    public BitDeleteDialog delete() {
        portal.click(portal.visibleButton("Delete"));
        return new BitDeleteDialog(portal);
    }

    private WebElement venueField() {
        return portal.find(By.name("venue_name"));
    }

    private void setField(By by, String value) {
        setField(portal.until(ExpectedConditions.presenceOfElementLocated(by)), value);
    }

    /** Types the value when the field holds something else; a picker's matching option is clicked. */
    private void setField(WebElement field, String value) {
        if (value.equals(field.getDomProperty("value"))) {
            return;
        }
        portal.clearAndType(field, value);
        portal.pause();
        portal.findAll(By.xpath("//*[@role='option' or self::li]")).stream()
                .filter(WebElement::isDisplayed).filter(e -> value.equals(e.getText().trim()))
                .findFirst().ifPresentOrElse(portal::click, () -> portal.pressTab(field));
    }
}
