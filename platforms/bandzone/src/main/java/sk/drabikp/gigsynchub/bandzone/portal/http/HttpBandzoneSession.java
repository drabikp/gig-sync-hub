package sk.drabikp.gigsynchub.bandzone.portal.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sk.drabikp.gigsynchub.bandzone.BandzonePortalException;
import sk.drabikp.gigsynchub.bandzone.portal.BandzonePageData;
import sk.drabikp.gigsynchub.bandzone.portal.BandzoneSession;
import sk.drabikp.gigsynchub.bandzone.portal.PosterDownloader;
import sk.drabikp.gigsynchub.gig.domain.Gig;
import sk.drabikp.gigsynchub.sync.application.port.out.FailureKind;
import tools.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One signed-in account working through Bandzone's concert pages over plain HTTP — the calls the pages
 * themselves make since the redesign (2026-11-22): the new-concert wizard's one create, the edit form's
 * save (its basic details, its tickets, its poster — then read back from the edit page), its lineup tab
 * ({@link LineupTab}), its state (cancelled) and its deletion (checked on the public page). Each operation
 * fails alone; the session stays usable.
 */
final class HttpBandzoneSession implements BandzoneSession {

    private static final Logger logger = LoggerFactory.getLogger(HttpBandzoneSession.class);

    /** The band this session publishes for, as the account's own bands list it. */
    record OwnBand(long id, String slug, String name) {
    }

    private final BandzoneWeb web;
    private final String bandSlug;
    private final PosterDownloader posters;
    private OwnBand ownBand; // read once per session from the account's bands

    HttpBandzoneSession(BandzoneWeb web, String bandSlug, PosterDownloader posters) {
        this.web = web;
        this.bandSlug = bandSlug;
        this.posters = posters;
    }

    /**
     * Creates the concert as the wizard does, in one call: when, where, what it is called and costs, and
     * the band itself performing. Interrupted part-way, it may exist already, so it fails for the user to
     * check instead of running again later.
     */
    @Override
    public String createGig(Gig gig) throws BandzonePortalException {
        try {
            OwnBand own = ownBand();
            ConcertForm.Place place = new ConcertForm(web).place(gig.location());
            Map<String, Object> wizard = new LinkedHashMap<>();
            ZonedDateTime start = gig.schedule().start();
            wizard.put("startDate", ConcertForm.date(start));
            wizard.put("startTime", ConcertForm.time(start));
            if (gig.schedule().hasEnd()) {
                wizard.put("endDate", ConcertForm.date(gig.schedule().end()));
                wizard.put("endTime", ConcertForm.time(gig.schedule().end()));
            }
            wizard.put("cityId", place.cityId());
            if (place.club()) {
                wizard.put("venueId", place.venueId());
            }
            wizard.put("venueName", place.venueName());
            wizard.put("name", gig.title());
            wizard.put("bandIds", List.of(own.id()));
            wizard.put("guests", List.of());
            wizard.put("tickets", ConcertForm.tickets(gig.admission()));
            if (gig.description() != null && !gig.description().isBlank()) {
                wizard.put("info", gig.description());
            }
            if (gig.facebookUrl() != null && !gig.facebookUrl().isBlank()) {
                wizard.put("facebookUrl", gig.facebookUrl());
            }
            JsonNode created = web.send("POST", "/api/gig/create", wizard, "create '" + gig.title() + "'");
            long id = created.path("gigId").asLong();
            if (id <= 0) {
                throw BandzonePortalException.temporary("Gig '" + gig.title() + "' was submitted but Bandzone did not "
                        + "say which concert it made — check Bandzone before publishing it again.");
            }
            logger.info("Bandzone gig created: '{}' (id {})", gig.title(), id);
            return String.valueOf(id);
        } catch (BandzonePortalException e) {
            if (e.kind() != FailureKind.BUSY) {
                throw e;
            }
            throw BandzonePortalException.temporary(e.getMessage() + " — '" + gig.title()
                    + "' may be on Bandzone already; check there before publishing it again.", e);
        }
    }

    /**
     * Overwrites the concert's details, entry, poster and lineup, as the edit form's tabs save them, then
     * reads back what was stored.
     */
    @Override
    public void updateGig(String bandzoneId, Gig gig) throws BandzonePortalException {
        long id = concertId(bandzoneId);
        stored(id);                                   // there, and this login's to edit, before anything is sent
        if (gig.posterImageUrl() != null) {
            Path file = posters.download(gig.posterImageUrl());
            try {
                long blob = web.upload(file, "cover");
                web.send("PATCH", "/api/gig/" + id + "/cover", Map.of("blobId", blob), "set concert " + id + "'s poster");
            } finally {
                PosterDownloader.deleteQuietly(file);
            }
        }
        ConcertForm.Place place = new ConcertForm(web).place(gig.location());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("title", gig.title());
        details.put("dateFrom", BandzonePageData.format(gig.schedule().start()));
        details.put("dateUntil", gig.schedule().hasEnd() ? BandzonePageData.format(gig.schedule().end()) : null);
        ConcertForm.placeInto(details, place);
        details.put("facebookUrl", blankless(gig.facebookUrl()));
        details.put("description", blankless(gig.description()));
        web.send("PATCH", "/api/gig/" + id, details, "save the changes to concert " + id);
        web.send("PUT", "/api/gig/" + id + "/tickets", ConcertForm.tickets(gig.admission()),
                "save concert " + id + "'s entry");

        verifyStored(stored(id), gig, place);
        verifyTickets(id, gig);
        new LineupTab(web, id).sync(stored(id), gig.lineup(), ownBand(), () -> stored(id));
        logger.info("Bandzone gig updated: '{}' (id {})", gig.title(), id);
    }

    @Override
    public void cancelGig(String bandzoneId) throws BandzonePortalException {
        long id = concertId(bandzoneId);
        if (gone(id)) {
            throw BandzonePortalException.needsUser("Bandzone has no concert " + id
                    + " any more, so there is nothing to cancel — forget the link, or publish it again.");
        }
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("status", "cancelled");
        state.put("note", null);
        state.put("notify", false);
        web.send("POST", "/api/gig/" + id + "/status", state, "cancel concert " + id);
        if (!stored(id).cancelled()) {
            throw BandzonePortalException.temporary("Bandzone did not cancel concert " + id
                    + " — it is stored as not cancelled.");
        }
        logger.info("Bandzone gig cancelled (id {})", id);
    }

    @Override
    public void reactivateGig(String bandzoneId) throws BandzonePortalException {
        long id = concertId(bandzoneId);
        if (gone(id)) {
            throw BandzonePortalException.refused("Bandzone has no concert " + id + " any more");
        }
        StoredConcert stored = stored(id);
        if (stored.statusId() == StoredConcert.PLAYED) {
            throw BandzonePortalException.refused("Bandzone keeps a played concert's state as it is");
        }
        if (!stored.cancelled()) {
            logger.info("Bandzone concert {} is not cancelled — nothing to reactivate", id);
            return;
        }
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("status", "approved");
        state.put("note", null);
        state.put("notify", false);
        web.send("POST", "/api/gig/" + id + "/status", state, "make concert " + id + " public again");
        if (stored(id).statusId() != StoredConcert.PUBLIC) {
            throw BandzonePortalException.temporary("Bandzone did not make concert " + id
                    + " public again — it is still stored as cancelled.");
        }
        logger.info("Bandzone gig reactivated (id {})", id);
    }

    /**
     * Deletes the concert, unless it is gone already — asked first, on its public page: Bandzone answers a
     * delete of a concert it no longer has as if it had deleted it.
     */
    @Override
    public boolean deleteGig(String bandzoneId) throws BandzonePortalException {
        long id = concertId(bandzoneId);
        if (gone(id)) {
            logger.info("Bandzone concert {} is gone — already removed", id);
            return false;
        }
        web.send("DELETE", "/api/gig/" + id, null, "delete concert " + id);
        if (!gone(id)) {
            throw BandzonePortalException.temporary("Bandzone did not delete concert " + id + " — it is still there.");
        }
        logger.info("Bandzone gig deleted (id {})", id);
        return true;
    }

    @Override
    public String bandName() throws BandzonePortalException {
        return ownBand().name();
    }

    /** Nothing to release: the account's cookies stay for its next session. */
    @Override
    public void close() {
    }

    /**
     * The band at the account's address among the bands this login manages ({@code /api/user/bands}) —
     * which is also how a login that doesn't manage the band shows.
     */
    private OwnBand ownBand() throws BandzonePortalException {
        if (ownBand == null) {
            JsonNode bands = web.send("GET", "/api/user/bands", null, "list its bands").path("bands");
            List<String> managed = new ArrayList<>();
            for (JsonNode band : bands) {
                String slug = band.path("slug").asString("");
                managed.add(slug);
                if (slug.equalsIgnoreCase(bandSlug)) {
                    ownBand = new OwnBand(band.path("id").asLong(), slug, band.path("name").asString(slug));
                }
            }
            if (ownBand == null) {
                throw BandzonePortalException.needsUser("The Bandzone login connected doesn't manage the band '"
                        + bandSlug + "' (it manages " + (managed.isEmpty() ? "none" : String.join(", ", managed))
                        + ") — check the band's address in Settings.");
            }
        }
        return ownBand;
    }

    /** The concert as its edit page holds it; one this login may not edit, or one gone, needs the user. */
    private StoredConcert stored(long id) throws BandzonePortalException {
        BandzoneWeb.Page page = web.page("/koncert/" + id + "/upravit");
        if (page.status() == 404 || page.status() == 410) {
            throw BandzonePortalException.needsUser("Bandzone has no concert " + id
                    + " any more — forget the link, or publish it again.");
        }
        if (page.url().contains("forbidden=") || page.url().contains("/login")) {
            throw BandzonePortalException.needsUser("Bandzone doesn't let this login edit concert " + id
                    + " — was it made by another account?");
        }
        JsonNode gig = BandzonePageData.of(page.body()).object("gig", "{\"gigId\":" + id).orElseThrow(() ->
                BandzonePortalException.temporary("Bandzone's edit page of concert " + id
                        + " carries no concert — has Bandzone changed its pages?"));
        return StoredConcert.of(gig);
    }

    /** Positively gone: the concert's public page answers "not found" (a cancelled one still opens). */
    private boolean gone(long id) throws BandzonePortalException {
        int status = web.status("/koncert/" + id);
        return status == 404 || status == 410;
    }

    /** Compares what identifies the gig with what Bandzone stored. */
    private static void verifyStored(StoredConcert stored, Gig gig, ConcertForm.Place place)
            throws BandzonePortalException {
        List<String> mismatched = new ArrayList<>();
        compare(mismatched, "title", gig.title(), stored.title());
        compare(mismatched, "start", BandzonePageData.format(gig.schedule().start()),
                stored.start() == null ? null : BandzonePageData.format(stored.start()));
        compare(mismatched, "end", gig.schedule().hasEnd() ? BandzonePageData.format(gig.schedule().end()) : null,
                stored.end() == null ? null : BandzonePageData.format(stored.end()));
        compare(mismatched, "town", String.valueOf(place.cityId()), Objects.toString(stored.cityId(), null));
        if (place.club()) {
            compare(mismatched, "club", String.valueOf(place.venueId()), Objects.toString(stored.venueId(), null));
        } else {
            compare(mismatched, "venue", place.venueName(), stored.venueText());
        }
        compare(mismatched, "description", blankless(gig.description()), stored.description());
        compare(mismatched, "facebookUrl", blankless(gig.facebookUrl()), stored.facebookUrl());
        if (!mismatched.isEmpty()) {
            throw BandzonePortalException.temporary("Bandzone did not save the changes to concert " + stored.id() + ": "
                    + String.join(", ", mismatched));
        }
    }

    private void verifyTickets(long id, Gig gig) throws BandzonePortalException {
        String kind = web.send("GET", "/api/gig/" + id + "/tickets", null, "read concert " + id + "'s entry")
                .path("kind").asString("");
        String expected = ConcertForm.kind(gig.admission());
        if (!expected.equals(kind)) {
            throw BandzonePortalException.temporary("Bandzone did not save concert " + id + "'s entry: '" + kind
                    + "' (expected '" + expected + "')");
        }
    }

    private static void compare(List<String> mismatched, String field, String expected, String stored) {
        String want = expected == null ? "" : expected.strip();
        String got = stored == null ? "" : stored.strip();
        if (!want.equalsIgnoreCase(got)) {
            mismatched.add(field + "='" + got + "' (expected '" + want + "')");
        }
    }

    private static String blankless(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static long concertId(String bandzoneId) throws BandzonePortalException {
        try {
            return Long.parseLong(bandzoneId.strip());
        } catch (NumberFormatException e) {
            throw BandzonePortalException.needsUser("'" + bandzoneId + "' is no Bandzone concert id.");
        }
    }
}
