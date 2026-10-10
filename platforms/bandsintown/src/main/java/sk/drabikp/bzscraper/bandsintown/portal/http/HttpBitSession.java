package sk.drabikp.bzscraper.bandsintown.portal.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sk.drabikp.bzscraper.bandsintown.BitPortalException;
import sk.drabikp.bzscraper.bandsintown.RemovalReason;
import sk.drabikp.bzscraper.bandsintown.csv.BandsintownCsv;
import sk.drabikp.bzscraper.bandsintown.portal.BitEvent;
import sk.drabikp.bzscraper.bandsintown.portal.BitEventMapper;
import sk.drabikp.bzscraper.bandsintown.portal.BitPlaces;
import sk.drabikp.bzscraper.bandsintown.portal.BitSession;
import sk.drabikp.bzscraper.bandsintown.portal.HumanPacer;
import sk.drabikp.bzscraper.bandsintown.portal.PortalReply;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.TextFold;
import sk.drabikp.bzscraper.importing.domain.ImportedGig;
import sk.drabikp.bzscraper.sync.application.port.out.FailureKind;

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
                results.add(Created.published(gig, id, BitPlaces.check(gig, event)));
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
        return results;
    }

    /**
     * Changes the one event, past events included — the upload refuses those, this doesn't: when the band
     * plays, the name and the description. NOT where it is: asked to move an event's venue, Bandsintown
     * leaves the request hanging until its own gateway gives up (HTTP 504, checked against the real portal
     * on 2026-10-10), so a gig whose place has changed is the band's to correct there — said plainly
     * rather than retried. A note when Bandsintown had placed it far from the gig's town.
     */
    @Override
    public Optional<String> editEventInForm(String eventId, Gig gig) throws BitPortalException {
        BitEvent stored = event(eventId).orElseThrow(() -> BitPortalException.temporary(
                "Bandsintown doesn't give event " + eventId + " — nothing was changed."));
        refuseAPlaceChange(eventId, gig, stored);
        ZonedDateTime start = gig.schedule().showStart();
        ZonedDateTime end = gig.schedule().showEnd();
        // the event's own fields, and the state it stays in — the portal insists on being told that even
        // when it isn't changing
        Map<String, Object> change = new LinkedHashMap<>();
        change.put("version", stored.version());
        change.put("status", stored.status());
        change.put("start_date", start.format(DATE));
        change.put("start_time", start.format(TIME));
        change.put("end_date", end == null ? null : end.format(DATE));
        change.put("end_time", end == null ? null : end.format(TIME));
        change.put("title", gig.title());
        if (gig.description() != null) {
            change.put("description", gig.description());
        }
        PortalReply reply = api.patch(api.actorPath("events/" + eventId), change);
        if (!reply.ok()) {
            throw BitPortalException.refused("Bandsintown didn't save event " + eventId + " — "
                    + (reply.rowErrors().isEmpty() ? reply.describe() : String.join(", ", reply.rowErrors())));
        }
        return reply.event().map(saved -> BitPlaces.check(gig, saved));
    }

    /**
     * Stops before changing anything when the gig's place is no longer the event's: the venue by name
     * (either containing the other, as the same place is written both ways) or the town.
     */
    private void refuseAPlaceChange(String eventId, Gig gig, BitEvent stored) throws BitPortalException {
        if (!sameName(gig.location().venue(), stored.venueName())
                || !sameName(gig.location().city(), stored.venueCity())) {
            throw BitPortalException.needsUser("Bandsintown won't move event " + eventId + " to "
                    + where(gig) + " from here (it only ever times out) — change its place on "
                    + "Bandsintown itself, then the rest follows.");
        }
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
