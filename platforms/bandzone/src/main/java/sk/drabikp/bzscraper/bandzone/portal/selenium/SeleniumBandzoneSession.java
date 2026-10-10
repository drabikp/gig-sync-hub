package sk.drabikp.bzscraper.bandzone.portal.selenium;

import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sk.drabikp.bzscraper.bandzone.BandzonePortalException;
import sk.drabikp.bzscraper.bandzone.portal.BandzoneSession;
import sk.drabikp.bzscraper.browser.PlatformBrowser;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.sync.application.port.out.FailureKind;

/**
 * One logged-in Bandzone browser, working through the band admin's pages — each a page object
 * ({@link BandzoneCreateWizard}, {@link BandzoneUpdateForm}, {@link BandzoneDeleteTab},
 * {@link BandzoneLineupPage}). Anything unexpected from the browser fails only that gig, as a
 * temporary failure; the session stays open for the rest.
 */
final class SeleniumBandzoneSession implements BandzoneSession {

    private static final Logger logger = LoggerFactory.getLogger(SeleniumBandzoneSession.class);

    private final PlatformBrowser.Lease lease;
    private final BandzoneBrowser browser;
    private final PosterDownloader posters;
    private String ownBandName; // read once per session from the band's profile

    SeleniumBandzoneSession(PlatformBrowser.Lease lease, BandzoneBrowser browser, PosterDownloader posters) {
        this.lease = lease;
        this.browser = browser;
        this.posters = posters;
    }

    /**
     * Creates the concert. Interrupted part-way, it may exist already, so it is not postponed (a
     * run later would make it twice) but fails for the user to check.
     */
    @Override
    public String createGig(Gig gig) throws BandzonePortalException {
        try {
            return guarded("create", "Bandzone create failed for '" + gig.title() + "'", () -> {
                String id = new BandzoneCreateWizard(browser).create(gig);
                logger.info("Bandzone gig created: '{}' (id {})", gig.title(), id);
                return id;
            });
        } catch (BandzonePortalException e) {
            if (e.kind() != FailureKind.BUSY) {
                throw e;
            }
            throw BandzonePortalException.temporary(e.getMessage() + " — '" + gig.title()
                    + "' may be on Bandzone already; check there before publishing it again.", e);
        }
    }

    @Override
    public void updateGig(String bandzoneId, Gig gig) throws BandzonePortalException {
        guarded("update", "Bandzone update failed for concert " + bandzoneId, () -> {
            new BandzoneUpdateForm(browser, posters, bandzoneId).save(gig);
            new BandzoneLineupPage(browser, bandzoneId).sync(gig.lineup(), ownBandName());
            logger.info("Bandzone gig updated: '{}' (id {})", gig.title(), bandzoneId);
            return null;
        });
    }

    @Override
    public void cancelGig(String bandzoneId) throws BandzonePortalException {
        guarded("cancel", "Bandzone cancel failed for concert " + bandzoneId, () -> {
            if (!new BandzoneDeleteTab(browser, bandzoneId).submit(BandzoneDeleteTab.Action.CANCEL)) {
                throw BandzonePortalException.needsUser("Bandzone has no concert " + bandzoneId
                        + " any more, so there is nothing to cancel — forget the link, or publish it again.");
            }
            logger.info("Bandzone gig cancelled (id {})", bandzoneId);
            return null;
        });
    }

    @Override
    public boolean deleteGig(String bandzoneId) throws BandzonePortalException {
        return guarded("delete", "Bandzone delete failed for concert " + bandzoneId, () -> {
            boolean deleted = new BandzoneDeleteTab(browser, bandzoneId).submit(BandzoneDeleteTab.Action.DELETE);
            logger.info(deleted ? "Bandzone gig deleted (id {})" : "Bandzone concert {} is gone — already removed",
                    bandzoneId);
            return deleted;
        });
    }

    /** Hands the browser back (kept open a little for the next session). Never throws. */
    @Override
    public void close() {
        lease.keepWarm();
    }

    @Override
    public String bandName() throws BandzonePortalException {
        return guarded("band", "Reading the band's page failed", this::ownBandName);
    }

    /**
     * The publishing band's display name, as it appears in a concert's performer list. Read
     * from {@code og:title}: the profile's {@code <h1>} also holds genre and city.
     */
    private String ownBandName() throws BandzonePortalException {
        if (ownBandName == null) {
            browser.open("/" + browser.bandSlug());
            String name = browser.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("meta[property='og:title']"))).getDomAttribute("content");
            if (name == null || name.isBlank()) {
                throw BandzonePortalException.temporary("Could not read the band name of '" + browser.bandSlug() + "'.");
            }
            ownBandName = name.trim();
        }
        return ownBandName;
    }

    /** One operation: a failure leaves a screenshot; a browser error fails it as temporary. */
    private <T> T guarded(String step, String failure, PlatformBrowser.Work<T, BandzonePortalException> work)
            throws BandzonePortalException {
        return lease.guarded(step, failure, SeleniumBandzonePortalClient.FAILURES, work);
    }
}
