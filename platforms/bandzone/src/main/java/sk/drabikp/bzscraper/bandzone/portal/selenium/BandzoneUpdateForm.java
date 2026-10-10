package sk.drabikp.bzscraper.bandzone.portal.selenium;

import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import sk.drabikp.bzscraper.bandzone.BandzonePortalException;
import sk.drabikp.bzscraper.gig.domain.Gig;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A concert's edit form ({@code /koncert/<id>/update}): poster, town, club, dates, info —
 * saved, then read back, since a rejected form re-renders with the submitted values.
 * Autocomplete picks and the poster upload re-render the form via AJAX, so they go first.
 */
final class BandzoneUpdateForm {

    private static final Duration UPLOAD_WAIT = Duration.ofSeconds(60);

    private final BandzoneBrowser browser;
    private final BandzoneForm form;
    private final PosterDownloader posters;
    private final String bandzoneId;

    public BandzoneUpdateForm(BandzoneBrowser browser, PosterDownloader posters, String bandzoneId) {
        this.browser = browser;
        this.form = new BandzoneForm(browser);
        this.posters = posters;
        this.bandzoneId = bandzoneId;
    }

    /** Opens the form, sets every field from the gig, saves and checks what Bandzone stored. */
    public void save(Gig gig) throws BandzonePortalException {
        open();
        if (gig.posterImageUrl() != null) {
            uploadPoster(gig.posterImageUrl());
        }
        form.selectCity(gig.location());
        form.selectVenue(gig.location().venue());
        form.setStart(gig.schedule().start());
        form.setEnd(gig.schedule().hasEnd() ? gig.schedule().end() : null);
        form.fillInfo(gig);
        WebElement send = browser.find(By.id("frmupdateForm-send"));
        browser.click(send);
        browser.until(ExpectedConditions.stalenessOf(send));
        verifyStored(gig);
    }

    private void open() {
        browser.open("/koncert/" + bandzoneId + "/update?updateTabs-at=updateForm");
        browser.until(ExpectedConditions.presenceOfElementLocated(By.id("frmupdateForm-send")));
    }

    /** Re-reads the stored concert and compares the fields that identify the gig. */
    private void verifyStored(Gig gig) throws BandzonePortalException {
        open();
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("name", gig.title());
        expected.put("start[date]", gig.schedule().start().format(BandzoneForm.DATE));
        expected.put("start[time]", gig.schedule().start().format(BandzoneForm.TIME));
        expected.put("end[date]", gig.schedule().hasEnd() ? gig.schedule().end().format(BandzoneForm.DATE) : "");
        expected.put("cityId__container[textInput]", gig.location().city());
        expected.put("venueId__container[textInput]", gig.location().venue() == null ? "" : gig.location().venue());
        expected.put("entry", gig.admission().isPaid() ? gig.admission().amount() : "");
        expected.put("info", gig.description() == null ? "" : gig.description());
        List<String> mismatched = new ArrayList<>();
        expected.forEach((field, value) -> {
            String stored = browser.find(By.name(field)).getDomProperty("value");
            if (!value.equalsIgnoreCase(stored == null ? "" : stored.trim())) {
                mismatched.add(field + "='" + stored + "' (expected '" + value + "')");
            }
        });
        String storedType = (String) browser.js("var r=document.querySelector('[name=entryType]:checked');return r?r.value:'';");
        String expectedType = BandzoneForm.entryTypeValue(gig.admission().type());
        if (!expectedType.equals(storedType)) {
            mismatched.add("entryType='" + storedType + "' (expected '" + expectedType + "')");
        }
        if (!mismatched.isEmpty()) {
            throw BandzonePortalException.temporary("Bandzone did not save the changes to concert " + bandzoneId + ": "
                    + String.join(", ", mismatched));
        }
    }

    /**
     * Downloads the poster and uploads it through the form's auto-uploading file input
     * (Bandzone keeps one image; a new upload replaces it). Bandzone offers no way to remove a
     * poster, so a gig without one leaves the current image alone.
     */
    private void uploadPoster(String posterUrl) throws BandzonePortalException {
        Path file = posters.download(posterUrl);
        try {
            By input = By.name("profileImage[uploader]");
            browser.until(d -> d.findElement(input).isEnabled()); // enabled once the uploader's JS is ready
            WebElement formBefore = browser.find(By.id("frmupdateForm-send"));
            browser.find(input).sendKeys(file.toAbsolutePath().toString());
            browser.until(UPLOAD_WAIT, d -> (Boolean) browser.js(
                    "var c=document.querySelector('[id^=files-container]');"
                            + "return !!c && /Nahráno/.test(c.innerText) && !c.querySelector('.template-upload');"));
            browser.awaitReload(formBefore);       // the uploader reloads the form when done
            browser.until(ExpectedConditions.presenceOfElementLocated(By.id("frmupdateForm-send")));
        } catch (TimeoutException e) {
            throw BandzonePortalException.temporary("Poster upload did not finish: " + posterUrl, e);
        } finally {
            PosterDownloader.deleteQuietly(file);
        }
    }
}
