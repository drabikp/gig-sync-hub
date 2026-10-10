package sk.drabikp.bzscraper.bandzone.portal.http;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sk.drabikp.bzscraper.bandzone.BandzonePortalException;
import sk.drabikp.bzscraper.bandzone.portal.BandzoneSession;
import sk.drabikp.bzscraper.bandzone.portal.PosterDownloader;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.sync.application.port.out.FailureKind;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One logged-in account working through the band admin's pages over plain HTTP — what the pages
 * themselves send: the 2-step new-concert wizard, the concert's edit form (poster, town, club, dates,
 * info — saved, then read back), its performers tab ({@link LineupTab}) and its delete tab (cancel or
 * delete, then checked on the public pages). Each operation fails alone; the session stays usable.
 */
final class HttpBandzoneSession implements BandzoneSession {

    private static final Logger logger = LoggerFactory.getLogger(HttpBandzoneSession.class);
    private static final Pattern CONCERT_URL = Pattern.compile("/koncert/(\\d+)");
    private static final String STEP_ONE = "gigDatePlaceBandsForm-submit";
    private static final String SIMILAR = "similarGigsForm-submit";
    private static final String INFO = "gigInfoForm-submit";
    private static final String UPDATE = "updateForm-submit";
    private static final String DELETE_TAB = "profileDeleteForm-submit";

    private final BandzoneWeb web;
    private final String bandSlug;
    private final PosterDownloader posters;
    private String ownBandName; // read once per session from the band's profile

    HttpBandzoneSession(BandzoneWeb web, String bandSlug, PosterDownloader posters) {
        this.web = web;
        this.bandSlug = bandSlug;
        this.posters = posters;
    }

    /**
     * Creates the concert: date, time and town → (maybe "similar concerts?", answered "new") → name,
     * entry, info, Facebook → Bandzone sends to the created concert, whose id is in that address.
     * Interrupted part-way, it may exist already, so it fails for the user to check instead of
     * running again later.
     */
    @Override
    public String createGig(Gig gig) throws BandzonePortalException {
        try {
            Document wizard = web.get("/koncert/zalozit.html?initBandPublicId=" + bandSlug).document();
            ConcertForm when = new ConcertForm(web, form(wizard, STEP_ONE, "the new-concert wizard"), STEP_ONE);
            when.setStart(gig.schedule().start());
            when.selectCity(gig.location());
            String next = when.send("continue", "the concert's date and town");
            if (next.contains("podobne-koncerty")) {
                NetteForm similar = form(web.get(next).document(), SIMILAR, "the similar-concerts question");
                next = new ConcertForm(web, similar, SIMILAR).send("new", "\"a new concert\"");
            }
            ConcertForm info = new ConcertForm(web, form(web.get(next).document(), INFO, "the wizard's info step"), INFO);
            info.fillInfo(gig);
            String created = info.send("send", "the new concert");
            Matcher id = CONCERT_URL.matcher(created);
            if (!id.find()) {
                throw BandzonePortalException.temporary("Gig '" + gig.title() + "' was submitted but Bandzone did not "
                        + "open the created concert (it sent to " + created + ").");
            }
            logger.info("Bandzone gig created: '{}' (id {})", gig.title(), id.group(1));
            return id.group(1);
        } catch (BandzonePortalException e) {
            if (e.kind() != FailureKind.BUSY) {
                throw e;
            }
            throw BandzonePortalException.temporary(e.getMessage() + " — '" + gig.title()
                    + "' may be on Bandzone already; check there before publishing it again.", e);
        } catch (IllegalStateException e) {
            throw changed("create '" + gig.title() + "'", e);
        }
    }

    /**
     * Overwrites the concert's details and lineup. The poster upload and the pickers re-render the
     * form, so they go first; then the plain fields, the save, and a read-back of what was stored.
     */
    @Override
    public void updateGig(String bandzoneId, Gig gig) throws BandzonePortalException {
        try {
            ConcertForm edit = new ConcertForm(web, form(web.get(updatePath(bandzoneId)).document(), UPDATE,
                    "concert " + bandzoneId + "'s edit form"), UPDATE);
            if (gig.posterImageUrl() != null) {
                Path file = posters.download(gig.posterImageUrl());
                try {
                    edit.uploadPoster(file);
                } finally {
                    PosterDownloader.deleteQuietly(file);
                }
            }
            edit.selectCity(gig.location());
            edit.selectVenue(gig.location().venue());
            edit.setStart(gig.schedule().start());
            edit.setEnd(gig.schedule().hasEnd() ? gig.schedule().end() : null);
            edit.fillInfo(gig);
            edit.send("send", "the changes to concert " + bandzoneId);
            verifyStored(bandzoneId, gig);
            new LineupTab(web, bandzoneId).sync(gig.lineup(), ownBandName());
            logger.info("Bandzone gig updated: '{}' (id {})", gig.title(), bandzoneId);
        } catch (IllegalStateException e) {
            throw changed("update concert " + bandzoneId, e);
        }
    }

    @Override
    public void cancelGig(String bandzoneId) throws BandzonePortalException {
        if (!submitDeleteTab(bandzoneId, "cancelGig")) {
            throw BandzonePortalException.needsUser("Bandzone has no concert " + bandzoneId
                    + " any more, so there is nothing to cancel — forget the link, or publish it again.");
        }
        verifyCancelled(bandzoneId);
        logger.info("Bandzone gig cancelled (id {})", bandzoneId);
    }

    @Override
    public boolean deleteGig(String bandzoneId) throws BandzonePortalException {
        if (!submitDeleteTab(bandzoneId, "delete")) {
            logger.info("Bandzone concert {} is gone — already removed", bandzoneId);
            return false;
        }
        if (!gone(bandzoneId)) {
            throw BandzonePortalException.temporary("Bandzone did not delete concert " + bandzoneId
                    + " — it is still there.");
        }
        logger.info("Bandzone gig deleted (id {})", bandzoneId);
        return true;
    }

    @Override
    public String bandName() throws BandzonePortalException {
        return ownBandName();
    }

    /** Nothing to release: the account's cookies stay for its next session. */
    @Override
    public void close() {
    }

    /**
     * Presses the delete tab's button (only that one), unless the concert is gone already — asked
     * first: Bandzone still shows a deleted concert's admin pages, delete button and all.
     */
    private boolean submitDeleteTab(String bandzoneId, String button) throws BandzonePortalException {
        if (gone(bandzoneId)) {
            return false;
        }
        try {
            NetteForm tab = form(web.get("/koncert/" + bandzoneId + "/update?updateTabs-at=profileDeleteForm").document(),
                    DELETE_TAB, "concert " + bandzoneId + "'s delete tab");
            new ConcertForm(web, tab, DELETE_TAB).send(button, "\"" + button + "\" for concert " + bandzoneId);
            return true;
        } catch (IllegalStateException e) {
            throw changed(button + " concert " + bandzoneId, e);
        }
    }

    /** Positively gone: the concert's public page answers "not found" (a cancelled one still opens). */
    private boolean gone(String bandzoneId) throws BandzonePortalException {
        int status = web.status("/koncert/" + bandzoneId);
        return status == 404 || status == 410;
    }

    /** The band page lists the concert as cancelled: on its planned tab, or on the tab of its year. */
    private void verifyCancelled(String bandzoneId) throws BandzonePortalException {
        Optional<Boolean> cancelled = listedCancelled(bandzoneId, "");
        if (cancelled.isEmpty()) {
            cancelled = listedCancelled(bandzoneId, "&gy=" + year(bandzoneId));
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

    private Optional<Boolean> listedCancelled(String bandzoneId, String tab) throws BandzonePortalException {
        Document page = web.get("/" + bandSlug + "?at=gig" + tab).document();
        Element link = page.selectFirst("article.gig a.gig__link[href*=\"/koncert/" + bandzoneId + "-\"]");
        return link == null ? Optional.empty()
                : Optional.of(link.closest("article.gig").hasClass("gig--canceled"));
    }

    /** The year the concert starts in, from its edit form. */
    private int year(String bandzoneId) throws BandzonePortalException {
        String date = form(web.get(updatePath(bandzoneId)).document(), UPDATE, "concert " + bandzoneId + "'s edit form")
                .value("start[date]");
        try {
            return LocalDate.parse(date.trim(), ConcertForm.DATE).getYear();
        } catch (DateTimeParseException e) {
            throw BandzonePortalException.temporary("Could not read the date of concert " + bandzoneId
                    + " ('" + date + "').", e);
        }
    }

    /** Re-reads the stored concert and compares the fields that identify the gig. */
    private void verifyStored(String bandzoneId, Gig gig) throws BandzonePortalException {
        NetteForm stored = form(web.get(updatePath(bandzoneId)).document(), UPDATE, "concert " + bandzoneId + "'s edit form");
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("name", gig.title());
        expected.put("start[date]", gig.schedule().start().format(ConcertForm.DATE));
        expected.put("start[time]", gig.schedule().start().format(ConcertForm.TIME));
        expected.put("end[date]", gig.schedule().hasEnd() ? gig.schedule().end().format(ConcertForm.DATE) : "");
        expected.put("cityId__container[textInput]", gig.location().city());
        expected.put("venueId__container[textInput]", gig.location().venue() == null ? "" : gig.location().venue());
        expected.put("entry", gig.admission().isPaid() ? gig.admission().amount() : "");
        expected.put("info", gig.description() == null ? "" : gig.description());
        expected.put("entryType", ConcertForm.entryTypeValue(gig.admission().type()));
        List<String> mismatched = new ArrayList<>();
        expected.forEach((field, value) -> {
            String got = stored.value(field).trim();
            if (!value.trim().equalsIgnoreCase(got)) {
                mismatched.add(field + "='" + got + "' (expected '" + value + "')");
            }
        });
        if (!mismatched.isEmpty()) {
            throw BandzonePortalException.temporary("Bandzone did not save the changes to concert " + bandzoneId + ": "
                    + String.join(", ", mismatched));
        }
    }

    /**
     * The publishing band's display name, as it appears in a concert's performer list: the profile's
     * {@code og:title} (its {@code <h1>} also holds genre and town).
     */
    private String ownBandName() throws BandzonePortalException {
        if (ownBandName == null) {
            Element title = web.get("/" + bandSlug).document().selectFirst("meta[property=og:title]");
            String name = title == null ? "" : title.attr("content").trim();
            if (name.isEmpty()) {
                throw BandzonePortalException.temporary("Could not read the band name of '" + bandSlug + "'.");
            }
            ownBandName = name;
        }
        return ownBandName;
    }

    private static NetteForm form(Document page, String signal, String what) throws BandzonePortalException {
        return NetteForm.find(page, signal).orElseThrow(() -> BandzonePortalException.temporary(
                "Bandzone's page has no form for " + what + " (is the login still valid, or has the page changed?)."));
    }

    /** A field or button the page doesn't have: Bandzone changed its page. */
    private static BandzonePortalException changed(String what, IllegalStateException e) {
        return BandzonePortalException.temporary("Could not " + what + ": the page has no " + e.getMessage()
                + " — has Bandzone changed it?", e);
    }

    private static String updatePath(String bandzoneId) {
        return "/koncert/" + bandzoneId + "/update?updateTabs-at=updateForm";
    }
}
