package sk.drabikp.bzscraper.bandzone.portal.selenium;

import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.support.ui.ExpectedConditions;
import sk.drabikp.bzscraper.bandzone.BandzonePortalException;
import sk.drabikp.bzscraper.gig.domain.Gig;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The 2-step "new concert" wizard: date, time and town → (maybe "similar concerts?",
 * answered "new") → name, entry, info, Facebook → Bandzone opens the created concert at
 * {@code /koncert/<id>-…}; the id is read from that address.
 */
final class BandzoneCreateWizard {

    private static final Pattern CONCERT_URL = Pattern.compile("/koncert/(\\d+)");

    private final BandzoneBrowser browser;
    private final BandzoneForm form;

    public BandzoneCreateWizard(BandzoneBrowser browser) {
        this.browser = browser;
        this.form = new BandzoneForm(browser);
    }

    /** Creates the concert; returns its Bandzone id. */
    public String create(Gig gig) throws BandzonePortalException {
        browser.open("/koncert/zalozit.html?initBandPublicId=" + browser.bandSlug());
        browser.until(ExpectedConditions.presenceOfElementLocated(By.name("start[date]")));
        form.setStart(gig.schedule().start());
        form.selectCity(gig.location());
        browser.clickByName("continue");
        passDuplicateScreen();
        form.fillInfo(gig);
        return send(gig);
    }

    /**
     * Between the two steps Bandzone may list similar concerts (same date and city) and ask
     * whether this is one of them; "new" continues to the info step.
     */
    private void passDuplicateScreen() {
        browser.until(ExpectedConditions.or(
                ExpectedConditions.presenceOfElementLocated(By.name("new")),
                ExpectedConditions.presenceOfElementLocated(By.name("name"))));
        if (browser.has(By.name("new"))) {
            browser.clickByName("new");
            browser.until(ExpectedConditions.presenceOfElementLocated(By.name("name")));
        }
    }

    /**
     * Submits and reads the new concert id from the page Bandzone opens. (Searching the band
     * page by title is unreliable: same-titled gigs and the notification panel link to others.)
     */
    private String send(Gig gig) throws BandzonePortalException {
        browser.click(browser.find(By.name("send")));
        try {
            browser.until(d -> CONCERT_URL.matcher(d.getCurrentUrl()).find());
        } catch (TimeoutException e) {
            throw BandzonePortalException.temporary("Gig '" + gig.title() + "' was submitted but Bandzone did not open "
                    + "the created concert (still at " + browser.currentUrl() + ").", e);
        }
        Matcher m = CONCERT_URL.matcher(browser.currentUrl());
        m.find();
        return m.group(1);
    }
}
