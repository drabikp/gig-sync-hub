package sk.drabikp.bzscraper.bandzone.portal.selenium;

import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import sk.drabikp.bzscraper.bandzone.BandzonePortalException;
import sk.drabikp.bzscraper.gig.domain.EntryType;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.Location;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * The concert fields the create wizard and the update form share (same names in both): the
 * date and time, the town (city search), the club (venue search), and the info fields.
 */
final class BandzoneForm {

    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d.M.yyyy");
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final Duration SUGGESTION_WAIT = Duration.ofSeconds(5);

    private final BandzoneBrowser browser;

    BandzoneForm(BandzoneBrowser browser) {
        this.browser = browser;
    }

    void setStart(ZonedDateTime start) {
        browser.setValue("[name='start[date]']", start.format(DATE));
        browser.setValue("[name='start[time]']", start.format(TIME));
    }

    /** The end, or none (cleared). */
    void setEnd(ZonedDateTime end) {
        browser.setValue("[name='end[date]']", end == null ? "" : end.format(DATE));
        browser.setValue("[name='end[time]']", end == null ? "" : end.format(TIME));
    }

    /**
     * Picks the town through the city search: the suggestion that fits by name, country and
     * district ({@link BandzoneTowns}); several fits or none → the user corrects the town,
     * never a guess. Then waits for {@code cityId} to take the picked town.
     */
    void selectCity(Location location) throws BandzonePortalException {
        String city = location.city();
        String currentText = browser.find(By.name("cityId__container[textInput]")).getDomProperty("value");
        String previousId = browser.find(By.name("cityId")).getDomProperty("value");
        // The form shows only the town's name, and a name may be the wrong town of several
        // (once: a Czech village for Košice) — a town picked from the place search is
        // therefore chosen again every time; only a merely typed one is kept as it is.
        if (location.district() == null && city.equalsIgnoreCase(currentText)
                && previousId != null && !previousId.isBlank()) {
            return;
        }
        browser.setValue("[name='cityId__container[textInput]']", city);
        browser.clickByName("cityId__container[searchButton]");

        List<BandzoneTowns.Suggestion> suggestions;
        try {
            suggestions = browser.until(d -> townSuggestions());
        } catch (TimeoutException e) {
            throw BandzonePortalException.needsUser("Bandzone's city search gave nothing for '" + city
                    + "' — correct the gig's city (the town's name, not a district) and try again.", e);
        }
        BandzoneTowns.Choice choice = BandzoneTowns.choose(city, location.country(), location.district(), suggestions);
        if (choice.pick() == null) {
            throw BandzonePortalException.needsUser(choice.problem());
        }
        WebElement cityIdInput = browser.find(By.name("cityId"));
        browser.click(browser.find(By.cssSelector("li[data-bz-town='" + choice.pick().index() + "']")));
        browser.awaitReload(cityIdInput);
        browser.until(d -> {
            String v = d.findElement(By.name("cityId")).getDomProperty("value");
            return v != null && !v.isBlank();
        });
    }

    /**
     * The city search's suggestions (name + "okres …, kraj …[, Slovensko]"), each marked
     * {@code data-bz-town=<index>} to click it; null while none are shown. The list's last
     * entry offers to add a new town — never used.
     */
    @SuppressWarnings("unchecked")
    private List<BandzoneTowns.Suggestion> townSuggestions() {
        List<List<Object>> rows = (List<List<Object>>) browser.js(
                "var items = Array.from(document.querySelectorAll('li')).filter(li => li.querySelector('.name'));"
                        + "var adds = Array.from(document.querySelectorAll('li')).some(li => /přidat nové město/i.test(li.innerText));"
                        + "if (!items.length && !adds) return null;"
                        + "return items.map((li, i) => { li.setAttribute('data-bz-town', i);"
                        + " var d = li.querySelector('.description');"
                        + " return [li.querySelector('.name').innerText.trim(), d ? d.innerText.trim() : '']; });");
        if (rows == null) {
            return null;
        }
        List<BandzoneTowns.Suggestion> suggestions = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            suggestions.add(new BandzoneTowns.Suggestion(i, String.valueOf(rows.get(i).get(0)),
                    String.valueOf(rows.get(i).get(1))));
        }
        return suggestions;
    }

    /**
     * Sets the venue. An exact (case-insensitive) match in Bandzone's club database links the
     * club; anything else is kept as free text, which Bandzone accepts. No venue clears it.
     */
    void selectVenue(String venue) {
        String currentText = browser.find(By.name("venueId__container[textInput]")).getDomProperty("value");
        if (venue != null && venue.equalsIgnoreCase(currentText)) {
            return;
        }
        browser.setValue("[name='venueId']", "");
        if (venue == null) {
            browser.setValue("[name='venueId__container[textInput]']", "");
            return;
        }
        browser.js("document.querySelectorAll('ul.ui-autocomplete').forEach(u=>{u.innerHTML='';u.style.display='none';});"
                        + "var t=document.querySelector('[name=\"venueId__container[textInput]\"]');"
                        + "t.focus();t.value=arguments[0];t.dispatchEvent(new Event('input',{bubbles:true}));",
                venue);
        try {
            browser.until(SUGGESTION_WAIT, d -> (Boolean) browser.js(
                    "return Array.from(document.querySelectorAll('ul.ui-autocomplete'))"
                            + ".some(u=>u.offsetParent!==null&&u.querySelector('li'));"));
        } catch (TimeoutException e) {
            keepFreeTextVenue(venue); // no club suggestions
            return;
        }
        WebElement venueIdInput = browser.find(By.name("venueId"));
        Boolean linked = (Boolean) browser.js("var n=arguments[0].toLowerCase();"
                        + "var m=Array.from(document.querySelectorAll('ul.ui-autocomplete li'))"
                        + ".filter(l=>l.offsetParent!==null)"
                        + ".find(l=>{var h=l.querySelector('h4.title');return h&&h.innerText.trim().toLowerCase()===n;});"
                        + "if(m){(m.querySelector('a')||m).click();return true;} return false;",
                venue);
        if (Boolean.TRUE.equals(linked)) {
            browser.awaitReload(venueIdInput);
            browser.until(d -> {
                String v = d.findElement(By.name("venueId")).getDomProperty("value");
                return v != null && !v.isBlank();
            });
        } else {
            keepFreeTextVenue(venue);
        }
    }

    /**
     * Closing the club menu without a pick makes the widget restore the previously linked
     * club's name, so the free text is written again — without events — and the link cleared.
     */
    private void keepFreeTextVenue(String venue) {
        browser.js("document.querySelectorAll('ul.ui-autocomplete').forEach(u=>u.style.display='none');"
                        + "var t=document.querySelector('[name=\"venueId__container[textInput]\"]');"
                        + "t.blur();t.value=arguments[0];"
                        + "document.querySelector('[name=\"venueId\"]').value='';",
                venue);
    }

    /** Sets every info field, blanking the optional ones the gig does not have (for edits). */
    void fillInfo(Gig gig) {
        browser.setValue("[name='name']", gig.title());
        selectEntryType(gig.admission().type());
        browser.setValue("[name='entry']", gig.admission().isPaid() ? gig.admission().amount() : "");
        browser.setValue("[name='info']", orEmpty(gig.description()));
        browser.setValue("[name='facebookUrl']", orEmpty(gig.facebookUrl()));
    }

    /** The {@code entryType} radio value Bandzone stores (0 = paid, 1 = voluntary, 2 = free). */
    static String entryTypeValue(EntryType entryType) {
        return switch (entryType == null ? EntryType.FREE : entryType) {
            case PAID -> "0";
            case VOLUNTARY -> "1";
            case FREE -> "2";
        };
    }

    private void selectEntryType(EntryType entryType) {
        String term = switch (entryType == null ? EntryType.FREE : entryType) {
            case FREE -> "zdarma";
            case VOLUNTARY -> "dobrovoln";
            case PAID -> "ručně";
        };
        browser.js("var term=arguments[0];"
                        + "var r=Array.from(document.querySelectorAll('[name=\"entryType\"]'))"
                        + ".find(x=>{var l=x.closest('label')||document.querySelector('label[for=\"'+x.id+'\"]');"
                        + "return l&&l.innerText.toLowerCase().includes(term)}); if(r){r.click();}",
                term);
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
