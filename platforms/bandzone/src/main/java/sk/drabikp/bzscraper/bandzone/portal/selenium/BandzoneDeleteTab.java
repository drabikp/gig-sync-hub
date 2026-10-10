package sk.drabikp.bzscraper.bandzone.portal.selenium;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import sk.drabikp.bzscraper.bandzone.BandzonePortalException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * A concert's delete tab: "cancel the concert" or "delete it" ({@link Action}). The form is
 * submitted with the button's value — a plain click would open the browser's confirm() dialog.
 * Then what Bandzone did is checked: a deleted concert is gone, a cancelled one is listed as
 * cancelled on the band's page. A concert that is gone answers its public page
 * ({@code /koncert/<id>}) with "not found" — as Bandzone did on 2026-10-09 right after a delete,
 * while its admin pages still opened.
 */
final class BandzoneDeleteTab {

    /** What the tab does, by the name of its button. */
    enum Action {
        CANCEL("cancelGig"),
        DELETE("delete");

        private final String button;

        Action(String button) {
            this.button = button;
        }
    }

    /** Asks for an address as the page would: its HTTP status (redirects followed). */
    private static final String STATUS = "var done=arguments[arguments.length-1];"
            + "fetch(arguments[0],{credentials:'same-origin'}).then(r=>done(r.status)).catch(e=>done(-1));";
    /** Whether the band page's gig list shows the concert cancelled; null when it doesn't list it. */
    private static final String LISTED_CANCELLED = "var a=document.querySelector("
            + "'article.gig a.gig__link[href*=\"/koncert/'+arguments[0]+'-\"]');"
            + "return a ? a.closest('article.gig').classList.contains('gig--canceled') : null;";

    private final BandzoneBrowser browser;
    private final String bandzoneId;

    public BandzoneDeleteTab(BandzoneBrowser browser, String bandzoneId) {
        this.browser = browser;
        this.bandzoneId = bandzoneId;
    }

    /**
     * Presses the action's button and checks that Bandzone did it. False when the concert is
     * gone already (nothing pressed).
     */
    public boolean submit(Action action) throws BandzonePortalException {
        Optional<WebElement> pressed = open(action);
        if (pressed.isEmpty()) {
            return false;
        }
        browser.js("var n=arguments[0];"
                        + "var b=document.querySelector('[name=\"'+n+'\"]'); var f=b?b.form:null;"
                        + "if(f){ if(b.value){var h=document.createElement('input');h.type='hidden';"
                        + "h.name=n;h.value=b.value;f.appendChild(h);}"
                        + " if(f.requestSubmit){f.requestSubmit(b);}else{f.submit();} }",
                action.button);
        browser.until(ExpectedConditions.stalenessOf(pressed.get()));     // Bandzone answered with a new page
        switch (action) {
            case DELETE -> verifyDeleted();
            case CANCEL -> verifyCancelled();
        }
        return true;
    }

    /**
     * The tab's button; empty when the concert is gone. Whether it is gone is asked first: Bandzone
     * still shows a deleted concert's admin pages, delete button and all (seen 2026-10-09).
     */
    private Optional<WebElement> open(Action action) {
        browser.open(tabPath());
        if (gone()) {
            return Optional.empty();
        }
        return Optional.of(browser.until(ExpectedConditions.presenceOfElementLocated(By.name(action.button))));
    }

    private void verifyDeleted() throws BandzonePortalException {
        if (!gone()) {
            throw BandzonePortalException.temporary("Bandzone did not delete concert " + bandzoneId
                    + " — it is still there.");
        }
    }

    /**
     * The band page lists the concert as cancelled: on its planned tab, or (a concert already
     * played) on the tab of its year.
     */
    private void verifyCancelled() throws BandzonePortalException {
        Optional<Boolean> cancelled = listedCancelled("");
        if (cancelled.isEmpty()) {
            cancelled = listedCancelled("&gy=" + year());
        }
        if (cancelled.isEmpty()) {
            throw BandzonePortalException.temporary("Bandzone's band page doesn't list concert " + bandzoneId
                    + ", so its cancelling couldn't be checked.");
        }
        if (!cancelled.get()) {
            throw BandzonePortalException.temporary("Bandzone did not cancel concert " + bandzoneId
                    + " — it is listed as not cancelled.");
        }
    }

    private Optional<Boolean> listedCancelled(String tab) {
        browser.open("/" + browser.bandSlug() + "?at=gig" + tab);
        return Optional.ofNullable((Boolean) browser.js(LISTED_CANCELLED, bandzoneId));
    }

    /** The year the concert starts in, from its edit form. */
    private int year() throws BandzonePortalException {
        browser.open("/koncert/" + bandzoneId + "/update?updateTabs-at=updateForm");
        String date = browser.until(ExpectedConditions.presenceOfElementLocated(By.name("start[date]")))
                .getDomProperty("value");
        try {
            return LocalDate.parse(date == null ? "" : date.trim(), BandzoneForm.DATE).getYear();
        } catch (DateTimeParseException e) {
            throw BandzonePortalException.temporary("Could not read the date of concert " + bandzoneId
                    + " ('" + date + "').", e);
        }
    }

    /**
     * Positively gone, not merely a page without the button (a lost login or a slow page hides it
     * too): the concert's public page answers "not found". A concert still there — cancelled
     * too — leads to its full page.
     */
    private boolean gone() {
        Object status = browser.jsAsync(STATUS, "/koncert/" + bandzoneId);
        return status instanceof Number n && (n.intValue() == 404 || n.intValue() == 410);
    }

    private String tabPath() {
        return "/koncert/" + bandzoneId + "/update?updateTabs-at=profileDeleteForm";
    }
}
