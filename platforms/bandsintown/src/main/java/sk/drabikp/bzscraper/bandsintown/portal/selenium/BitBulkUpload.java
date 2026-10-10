package sk.drabikp.bzscraper.bandsintown.portal.selenium;

import org.openqa.selenium.By;
import org.openqa.selenium.ElementNotInteractableException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sk.drabikp.bzscraper.bandsintown.BitPortalException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The Bulk Upload dialog: a CSV ({@link BandsintownCsv}) is uploaded; rows without an event
 * id become drafts, rows with one edit that event. After drafts the success dialog offers
 * "Notify my followers" and Publish; otherwise just OK. Drafts that couldn't be published are
 * left there for the user (publish or delete them) — uploading again would make more.
 */
final class BitBulkUpload {

    private static final Logger logger = LoggerFactory.getLogger(BitBulkUpload.class);

    /** Reads the success dialog's "Notify my followers" switch and marks what to click. */
    static final String READ_NOTIFY_SWITCH = """
            const box = document.querySelector('.notify');
            if (!box) return null;
            const input = box.querySelector('input[type=checkbox]');
            const sw = input || box.querySelector('[role=switch],[aria-checked]');
            if (!sw) return null;
            document.querySelectorAll('[data-bz-notify]').forEach(e => e.removeAttribute('data-bz-notify'));
            (input ? (input.closest('label') || input.parentElement) : sw).setAttribute('data-bz-notify', '1');
            return input ? input.checked : sw.getAttribute('aria-checked') === 'true';""";

    private final BitPortal portal;

    BitBulkUpload(BitPortal portal) {
        this.portal = portal;
    }

    /** Uploads the CSV and returns the portal's reply to it. */
    public PortalReply upload(String csv) throws BitPortalException {
        WebElement fileInput = portal.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("input[type=file]")));
        Path file = null;
        try {
            file = Files.createTempFile("bzscraper-bandsintown-", ".csv");
            Files.writeString(file, csv);
            portal.pause();
            attach(fileInput, file);
            portal.pause();
            int mark = portal.replies().count();
            portal.click(portal.visibleButton("Upload"));
            return portal.replies().await(mark, r -> "PATCH".equals(r.method()) && r.urlContains("status=DRAFT"),
                    "the upload");
        } catch (IOException e) {
            throw BitPortalException.temporary("Could not write the Bandsintown upload file: " + e.getMessage(), e);
        } finally {
            if (file != null) {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException e) {
                    logger.debug("Could not delete {}", file, e);
                }
            }
        }
    }

    /** Sets "Notify my followers" and publishes the uploaded drafts. */
    public void publishDrafts(boolean notifyFollowers) throws BitPortalException {
        setNotifySwitch(notifyFollowers);
        int mark = portal.replies().count();
        portal.pause();
        portal.click(portal.visibleButton("Publish"));
        PortalReply reply = portal.replies().await(mark, PortalReply::write, "publishing");
        if (reply.status() != 200) {
            throw BitPortalException.needsUser("Bandsintown did not publish the uploaded events — " + reply.describe()
                    + " They are left as drafts there.");
        }
        portal.pause();
    }

    /** Closes the success dialog of an upload that made no drafts. */
    public void ok() {
        portal.pause();
        portal.click(portal.visibleButton("OK"));
        portal.pause();
    }

    private void setNotifySwitch(boolean desired) throws BitPortalException {
        Object state = portal.js(READ_NOTIFY_SWITCH);
        if (state == null) {
            throw BitPortalException.needsUser("Could not find Bandsintown's \"Notify my followers\" switch, so "
                    + "nothing was published — the events are left as drafts there.");
        }
        if (!Boolean.valueOf(desired).equals(state)) {
            portal.pause();
            portal.click(portal.find(By.cssSelector("[data-bz-notify]")));
            if (!Boolean.valueOf(desired).equals(portal.js(READ_NOTIFY_SWITCH))) {
                throw BitPortalException.needsUser("Could not switch Bandsintown's \"Notify my followers\" "
                        + (desired ? "on" : "off") + ", so nothing was published — the events are left as drafts there.");
            }
        }
    }

    private void attach(WebElement fileInput, Path file) {
        try {
            fileInput.sendKeys(file.toAbsolutePath().toString());
        } catch (ElementNotInteractableException e) {
            portal.js("arguments[0].style.display='block';arguments[0].style.visibility='visible';", fileInput);
            fileInput.sendKeys(file.toAbsolutePath().toString());
        }
    }
}
