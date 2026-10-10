package sk.drabikp.bzscraper.bandsintown.portal.selenium;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import sk.drabikp.bzscraper.bandsintown.BitPortalException;
import sk.drabikp.bzscraper.bandsintown.RemovalReason;

/**
 * Bandsintown's "Are you sure you want to remove this event?" dialog, opened from a list
 * row's menu or the event's form: a reason, a detail, then the dialog's own Delete.
 * Everything is looked up INSIDE the dialog — the form behind it has dropdowns, text areas
 * and a Delete button of its own.
 */
final class BitDeleteDialog {

    private final BitPortal portal;

    BitDeleteDialog(BitPortal portal) {
        this.portal = portal;
    }

    /** Removes the event: reason "canceled" for a cancelled gig, else "other". */
    public void confirm(String eventId, RemovalReason reason) throws BitPortalException {
        portal.until(ExpectedConditions.presenceOfElementLocated(By.name("reason")));
        portal.pause();
        portal.click(dialog().findElement(By.cssSelector("button[aria-label='Toggle dropdown']")));
        portal.click(portal.until(ExpectedConditions.visibilityOfElementLocated(
                By.cssSelector("li[value='" + reason.name() + "'] button"))));
        if (portal.find(By.name("reason")).getDomProperty("value").isBlank()) {
            throw BitPortalException.temporary("Could not pick a reason in Bandsintown's delete dialog — nothing was deleted.");
        }
        portal.pause();
        portal.type(dialog().findElement(By.tagName("textarea")),
                reason.comment());

        int mark = portal.replies().count();
        portal.pause();
        portal.click(dialog().findElement(By.xpath(".//button[normalize-space()='Delete']")));
        PortalReply reply = portal.replies().await(mark, r -> r.write() && r.urlContains("/events/" + eventId),
                "the delete");
        if (!reply.event().map(BitEvent::deleted).orElse(false)) {
            throw BitPortalException.temporary("Bandsintown did not delete event " + eventId + " — " + reply.describe());
        }
        portal.pause();
    }

    /** The nearest box around the reason field that holds a Delete button (found anew — it re-renders). */
    private WebElement dialog() {
        return portal.find(By.xpath(
                "//input[@name='reason']/ancestor::*[.//button[normalize-space()='Delete']][1]"));
    }
}
