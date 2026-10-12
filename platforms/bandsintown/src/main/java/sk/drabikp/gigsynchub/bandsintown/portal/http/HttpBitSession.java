package sk.drabikp.gigsynchub.bandsintown.portal.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sk.drabikp.gigsynchub.bandsintown.BitPortalException;
import sk.drabikp.gigsynchub.bandsintown.RemovalReason;
import sk.drabikp.gigsynchub.bandsintown.csv.BandsintownCsv;
import sk.drabikp.gigsynchub.bandsintown.portal.BitEvent;
import sk.drabikp.gigsynchub.bandsintown.portal.BitEventMapper;
import sk.drabikp.gigsynchub.bandsintown.portal.BitPlaces;
import sk.drabikp.gigsynchub.bandsintown.portal.BitSession;
import sk.drabikp.gigsynchub.bandsintown.portal.HumanPacer;
import sk.drabikp.gigsynchub.bandsintown.portal.PortalReply;
import sk.drabikp.gigsynchub.gig.domain.Address;
import sk.drabikp.gigsynchub.gig.domain.Coordinates;
import sk.drabikp.gigsynchub.gig.domain.Gig;
import sk.drabikp.gigsynchub.gig.domain.Location;
import sk.drabikp.gigsynchub.gig.domain.TextFold;
import sk.drabikp.gigsynchub.importing.domain.ImportedGig;
import sk.drabikp.gigsynchub.sync.application.port.out.FailureKind;

import java.time.Clock;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * One band's artist-portal session over plain HTTP — the API the portal's own pages call, no browser:
 * <ul>
 *   <li><b>create</b> — the Bulk Upload of a spreadsheet without event ids (≤ 25 rows) makes drafts;
 *       the reply says which row became which event, each draft is then published, and the event
 *       lists are read back to see that it really is published.</li>
 *   <li><b>update</b> — the same upload with the rows carrying their event ids.</li>
 *   <li><b>delete</b> — the event's state set to removed, with a reason.</li>
 *   <li><b>list</b> — the upcoming and past events, the past ones a page at a time.</li>
 * </ul>
 * Anything the portal answers is read through {@link PortalReply}, exactly as the browser client reads
 * what the pages got, so both judge a reply the same way.
 */
final class HttpBitSession implements BitSession {

    private static final Logger logger = LoggerFactory.getLogger(HttpBitSession.class);
    /** As for the browser's list: a band's past events are many, but not endless. */
    private static final int MAX_PAST_PAGES = 50;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final BitApi api;
    private final String artistName;
    private final Clock clock;
    private final HumanPacer pacer;

    HttpBitSession(BitApi api, String artistName, Clock clock) {
        this(api, artistName, clock, new HumanPacer(0, 0));
    }

    HttpBitSession(BitApi api, String artistName, Clock clock, HumanPacer pacer) {
        this.pacer = pacer;
        this.api = api;
        this.artistName = artistName;
        this.clock = clock;
    }

    // --- create ---

    @Override
    public List<Created> createEvents(List<Gig> gigs, boolean notifyFollowers) {
        List<Created> results = new ArrayList<>();
        for (int from = 0; from < gigs.size(); from += MAX_ROWS_PER_UPLOAD) {
            List<Gig> batch = gigs.subList(from, Math.min(gigs.size(), from + MAX_ROWS_PER_UPLOAD));
            if (from > 0) {
                pacer.longPause();
            }
            try {
                results.addAll(createBatch(batch, notifyFollowers));
            } catch (BitPortalException e) {
                logger.warn("Bandsintown upload of {} gig(s) failed", batch.size(), e);
                // interrupted part-way, the events may exist already (as drafts): not postponed — a
                // run later would upload them twice — but failed for the user to check
                FailureKind kind = e.kind() == FailureKind.BUSY ? FailureKind.TEMPORARY : e.kind();
                String message = e.kind() == FailureKind.BUSY
                        ? e.getMessage() + " — the events may be on Bandsintown already; check there" : e.getMessage();
                batch.forEach(gig -> results.add(Created.failed(gig, kind, message)));
            }
        }
        return results;
    }

    private List<Created> createBatch(List<Gig> batch, boolean notifyFollowers) throws BitPortalException {
        // unverified: the upload's own address (recorded as a multipart PATCH of the events with
        // status=DRAFT) — a live create is the only way to be sure of it
        PortalReply reply = api.multipartPatch(api.actorPath("events?status=DRAFT"),
                BandsintownCsv.newEvents(batch, artistName, notifyFollowers));
        if (!reply.ok()) {
            throw reply.rowErrors().isEmpty()
                    ? BitPortalException.temporary("Bandsintown rejected the upload — " + reply.describe())
                    : BitPortalException.refused("Bandsintown refused the events: "
                            + String.join(", ", reply.rowErrors()));
        }
        String[] drafts = reply.draftIds(batch.size());
        String[] existing = reply.updatedIds(batch.size());
        publish(drafts);

        Map<String, BitEvent> listed = listed(batch);
        List<Created> results = new ArrayList<>();
        for (int i = 0; i < batch.size(); i++) {
            Gig gig = batch.get(i);
            String id = drafts[i] != null ? drafts[i] : existing[i];
            BitEvent event = id == null ? null : listed.get(id);
            if (id == null) {
                results.add(Created.failed(gig, FailureKind.REFUSED,
                        "Bandsintown rejected this row — " + reply.describe()));
            } else if (event != null && event.published()) {
                results.add(Created.published(gig, id, placedWhereTheGigIs(id, gig, event)));
            } else {
                results.add(Created.failed(gig, FailureKind.NEEDS_USER, "Uploaded to Bandsintown as event " + id
                        + ", but it is not published (" + (event == null ? "not listed" : event.status())
                        + ") — publish or delete it there."));
            }
        }
        return results;
    }

    /**
     * Publishes each uploaded draft. A draft the portal won't publish is left there and said so by the
     * read-back, which judges every gig on what the lists show rather than on this.
     */
    private void publish(String[] drafts) throws BitPortalException {
        for (String id : drafts) {
            if (id == null) {
                continue;
            }
            // unverified: publishing one event by setting its state, quoting the version it was read
            // with (the portal's Draft tab was recorded doing this, with the event's image as well)
            Optional<BitEvent> draft = event(id);
            Map<String, Object> change = new LinkedHashMap<>();
            change.put("status", "PUBLISHED");
            draft.map(BitEvent::version).ifPresent(version -> change.put("version", version));
            PortalReply reply = api.patch(api.actorPath("events/" + id), change);
            if (!reply.ok()) {
                logger.warn("Bandsintown did not publish draft {} — {}", id, reply.describe());
            }
        }
    }

    /**
     * The listed events by id: the upcoming list, plus the past list when the batch has gigs already
     * played (published past events are listed there, not as upcoming).
     */
    private Map<String, BitEvent> listed(List<Gig> batch) throws BitPortalException {
        Map<String, BitEvent> byId = new HashMap<>();
        events(false).forEach(event -> byId.put(event.id(), event));
        ZonedDateTime now = ZonedDateTime.now(clock);
        if (batch.stream().anyMatch(gig -> gig.schedule().showStart().isBefore(now))) {
            events(true).forEach(event -> byId.putIfAbsent(event.id(), event));
        }
        return byId;
    }

    // --- update ---

    /**
     * One upload of rows WITH event ids, one row per edit. Each row is judged on its own, by its
     * position: updated (its id among the updated events), refused (the portal's row error), or not
     * applied — a reply with errors may hold back the other rows too, so those simply try again.
     */
    @Override
    public List<Edited> updateEvents(List<EventEdit> edits) throws BitPortalException {
        PortalReply reply = api.multipartPatch(api.actorPath("events?status=DRAFT"),
                BandsintownCsv.updates(edits, artistName));
        int rows = edits.size();
        String[] updated = reply.updatedIds(rows);
        String[] drafts = reply.draftIds(rows);
        String[] refused = reply.rowErrorsByIndex(rows);
        if (!reply.ok() && Arrays.stream(refused).allMatch(Objects::isNull)) {
            throw BitPortalException.temporary("Bandsintown rejected the upload — " + reply.describe());
        }
        List<Edited> results = new ArrayList<>();
        for (int i = 0; i < rows; i++) {
            String eventId = edits.get(i).eventId();
            if (drafts[i] != null) {
                results.add(Edited.notUpdated(eventId, FailureKind.NEEDS_USER, "Bandsintown made a new draft "
                        + drafts[i] + " instead of editing event " + eventId + " — delete that draft there"));
            } else if (refused[i] != null) {
                results.add(Edited.notUpdated(eventId, FailureKind.REFUSED, "Bandsintown refused it: " + refused[i]));
            } else if (eventId.equals(updated[i])) {
                results.add(Edited.updated(eventId));
            } else {
                results.add(Edited.notUpdated(eventId, FailureKind.TEMPORARY, "not applied — " + reply.describe()));
            }
        }
        return placedWhereTheGigsAre(edits, results);
    }

    /**
     * The places this upload gave the events it changed, put right. An edit moves an event's place by
     * the same guesswork a create does — the row carries text and the portal geocodes it — so the
     * events are read back and each one checked, which nothing did here before: a bulk edit could move
     * an event to the wrong town and say nothing, and only the nightly check would ever notice.
     */
    private List<Edited> placedWhereTheGigsAre(List<EventEdit> edits, List<Edited> results)
            throws BitPortalException {
        if (results.stream().noneMatch(Edited::isUpdated)) {
            return results;
        }
        Map<String, BitEvent> listed = listed(edits.stream().map(EventEdit::gig).toList());
        List<Edited> checked = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            Edited result = results.get(i);
            BitEvent event = result.isUpdated() ? listed.get(result.eventId()) : null;
            checked.add(event == null ? result
                    : Edited.updated(result.eventId(),
                            placedWhereTheGigIs(result.eventId(), edits.get(i).gig(), event)));
        }
        return checked;
    }

    /**
     * The place an upload gave one event, put right. The upload can only ever GUESS a place: its
     * template has no coordinate columns, so the portal works the place out from the row's text, and
     * with little more than a village's name it has put an event 71 km away (Luková, 2026-10-09 — the
     * gig had no venue, and the place search had neither street nor postal code for the village).
     *
     * <p>The gig's own coordinates cannot travel in an upload, but they can in the one-event form
     * edit, which is the only way the portal lets a place be set outright. So when the read-back shows
     * the event far from the gig's town, it is set from the town there and then, in this session.
     * Anything else leaves the event in the wrong place with a note nobody can act on: a re-sync of an
     * UPCOMING gig goes through the upload again and guesses the same way, and only a gig already
     * played reaches the form edit at all (the upload refuses the past).
     *
     * <p>A note at all means the town HAS whereabouts — {@link BitPlaces} has nothing to measure
     * without them — so the form edit has what the portal insists on for a place of the band's own.
     *
     * @return what to keep against the work: nothing when the place was right, what was done when it
     *         had to be set, and what is still wrong with it when that didn't work
     */
    private String placedWhereTheGigIs(String eventId, Gig gig, BitEvent event) {
        OptionalDouble km = BitPlaces.kilometresOff(gig, event);
        if (km.isEmpty()) {
            return null;
        }
        try {
            String stillOff = editEventInForm(eventId, gig).orElse(null);
            return stillOff != null ? stillOff : BitPlaces.setFromTheTown(km.getAsDouble(), gig);
        } catch (BitPortalException e) {
            logger.warn("Bandsintown put event {} {} km from the gig's town and would not take the town's place",
                    eventId, Math.round(km.getAsDouble()), e);
            return BitPlaces.check(gig, event) + " (setting it from the town failed: " + e.getMessage() + ")";
        }
    }

    /**
     * Changes the one event, past events included — the upload refuses those, this doesn't: when the band
     * plays, the name, the description and where it is. The portal's own edit form replaces an event
     * whole rather than sending the fields that changed, so this does the same: it reads the event, lays
     * the gig over what came back and puts all of it back. A note when Bandsintown placed it far from the
     * gig's town.
     */
    @Override
    public Optional<String> editEventInForm(String eventId, Gig gig) throws BitPortalException {
        Map<String, Object> held = storedEvent(eventId);
        BitEvent stored = BitEvent.of(held);
        ZonedDateTime start = gig.schedule().showStart();
        ZonedDateTime end = gig.schedule().showEnd();
        // the event as the portal holds it — version, state and all — with the gig laid over it
        Map<String, Object> change = new LinkedHashMap<>(held);
        change.put("start_date", start.format(DATE));
        change.put("start_time", start.format(TIME));
        change.put("end_date", end == null ? null : end.format(DATE));
        change.put("end_time", end == null ? null : end.format(TIME));
        change.put("title", gig.title());
        if (gig.description() != null) {
            change.put("description", gig.description());
        }
        place(gig, stored, change);
        // no ?past= here, whatever the portal's own pages put on their list and bulk calls: asked for it
        // on one event it answers 400 "Unknown query parameter 'past'" (checked live 2026-10-10)
        PortalReply reply = api.put(api.actorPath("events/" + eventId), change);
        if (!reply.ok()) {
            throw BitPortalException.refused("Bandsintown didn't save event " + eventId + " — "
                    + (reply.rowErrors().isEmpty() ? reply.describe() : String.join(", ", reply.rowErrors())));
        }
        return reply.event().map(saved -> BitPlaces.check(gig, saved));
    }

    /**
     * The gig's place, written the way the portal writes one of its user's OWN places: the name, the
     * town, the country, the region, the street, the postal code, the town's coordinates and its zone,
     * with no venue id — an id belongs to one of Bandsintown's own venue pages, and the portal clears it
     * whenever the place is the user's.
     *
     * <p>The coordinates are what makes it a place at all: the portal's own form keeps a venue only when
     * it has a name, a town, a country AND whereabouts, and blanks the lot otherwise. So a town merely
     * typed into the gig's form leaves the event's place as it stands — and when the gig has meanwhile
     * moved somewhere else, that is the band's to sort out rather than something to do quietly.
     */
    private static void place(Gig gig, BitEvent stored, Map<String, Object> change) throws BitPortalException {
        Location location = gig.location();
        Address address = location.address();
        Optional<Coordinates> at = address == null ? Optional.empty() : address.coordinates();
        if (at.isEmpty()) {
            if (!sameName(location.venue(), stored.venueName()) || !sameName(location.city(), stored.venueCity())) {
                throw BitPortalException.needsUser("Bandsintown needs to know where " + where(gig)
                        + " is before it can move event " + stored.id() + " there — pick the town from the "
                        + "list in the gig's form, or change the place on Bandsintown itself.");
            }
            return;
        }
        change.put("venue_id", null);              // the band's own place, not one of Bandsintown's venues
        change.put("venue_name", location.displayVenue());
        change.put("venue_city", location.city());
        change.put("venue_country", location.countryName());
        change.put("venue_region", address.region());
        change.put("venue_address", address.street());
        change.put("venue_postal_code", address.postalCode());
        change.put("venue_latitude", at.get().latitude());
        change.put("venue_longitude", at.get().longitude());
        change.put("venue_timezone", location.timezone());
        change.put("venue_type", null);
        change.put("google_place_id", null);
        change.put("place_id", null);
        change.put("place_input", null);
    }

    /** The gig's place as a person would say it. */
    private static String where(Gig gig) {
        String venue = gig.location().venue();
        return venue == null ? gig.location().city() : venue + ", " + gig.location().city();
    }

    /** Whether two names are the same place: blank counts as unset, and one may spell out the other. */
    private static boolean sameName(String ours, String theirs) {
        String a = ours == null ? "" : TextFold.fold(ours);
        String b = theirs == null ? "" : TextFold.fold(theirs);
        return a.isEmpty() || b.isEmpty() || a.contains(b) || b.contains(a);
    }

    // --- delete ---

    /**
     * Bandsintown keeps no cancelled copy, so cancelling and deleting are the same removal, told apart
     * only by the reason.
     */
    @Override
    public void deleteEvent(String eventId, RemovalReason reason) throws BitPortalException {
        remove(eventId, reason);
    }

    /**
     * The same removal as {@link #deleteEvent}: the browser needed the event's own form for a past
     * event, which its list would not remove, but the API makes no such distinction.
     */
    @Override
    public void deleteEventInForm(String eventId, RemovalReason reason) throws BitPortalException {
        remove(eventId, reason);
    }

    private void remove(String eventId, RemovalReason reason) throws BitPortalException {
        Optional<BitEvent> stored = event(eventId);
        if (stored.isEmpty() || stored.get().deleted()) {
            logger.info("Bandsintown event {} is not there — already removed", eventId);
            return;
        }
        // unverified: that a removal is this state change carrying the reason and its details
        Map<String, Object> change = new LinkedHashMap<>();
        change.put("status", "DELETED");
        change.put("reason", reason.name());
        change.put("detail", reason.comment());
        change.put("version", stored.get().version());
        PortalReply reply = api.patch(api.actorPath("events/" + eventId), change);
        if (!reply.event().map(BitEvent::deleted).orElse(false)) {
            throw BitPortalException.temporary("Bandsintown did not delete event " + eventId + " — "
                    + reply.describe());
        }
        logger.info("Bandsintown event {} removed ({})", eventId, reason);
    }

    // --- list (import) ---

    @Override
    public List<ImportedGig> listEvents() throws BitPortalException {
        Map<String, BitEvent> byId = new LinkedHashMap<>();
        events(false).forEach(event -> byId.putIfAbsent(event.id(), event));
        events(true).forEach(event -> byId.putIfAbsent(event.id(), event));
        List<ImportedGig> gigs = new ArrayList<>();
        byId.values().forEach(event -> BitEventMapper.toImported(event).ifPresent(gigs::add));
        return gigs;
    }

    /**
     * One list, following the portal's own paging: the past events come twenty at a time, and what comes
     * next is asked for by the day the last page reached ({@code before}), not by a page number — the
     * portal says that day in its {@code x-next-page}.
     */
    private List<BitEvent> events(boolean past) throws BitPortalException {
        String what = past ? "the past events" : "the event list";
        PortalReply reply = answered(api.get(api.actorPath("events?past=" + past)), what);
        List<BitEvent> events = new ArrayList<>(reply.events());
        for (int page = 1; reply.hasNextPage() && page < MAX_PAST_PAGES; page++) {
            reply = answered(api.get(api.actorPath("events?past=" + past + "&before="
                    + URLEncoder.encode(reply.next(), StandardCharsets.UTF_8))), "more of " + what);
            events.addAll(reply.events());
        }
        return events;
    }

    /** The one event as the portal holds it; empty when it has none (deleted long ago, or never there). */
    private Optional<BitEvent> event(String eventId) throws BitPortalException {
        PortalReply reply = api.get(api.actorPath("events/" + eventId));
        return reply.ok() ? reply.event() : Optional.empty();
    }

    /**
     * The event's every field as the portal wrote it — what a change starts from, since the portal's own
     * form puts the whole event back rather than the fields that changed.
     */
    private Map<String, Object> storedEvent(String eventId) throws BitPortalException {
        PortalReply reply = api.get(api.actorPath("events/" + eventId));
        return (reply.ok() ? reply.payload() : Optional.<Map<String, Object>>empty())
                .orElseThrow(() -> BitPortalException.temporary(
                        "Bandsintown doesn't give event " + eventId + " — nothing was changed."));
    }

    private static PortalReply answered(PortalReply reply, String what) throws BitPortalException {
        if (!reply.ok()) {
            throw BitPortalException.temporary("Could not read " + what + " from Bandsintown — " + reply.describe());
        }
        return reply;
    }

    /** Nothing to release: the band's session is a token, not a browser. */
    @Override
    public void close() {
    }
}
