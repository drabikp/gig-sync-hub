package sk.drabikp.bzscraper.bandsintown.portal.selenium;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sk.drabikp.bzscraper.bandsintown.BitPortalException;
import sk.drabikp.bzscraper.bandsintown.RemovalReason;
import sk.drabikp.bzscraper.bandsintown.csv.BandsintownCsv;
import sk.drabikp.bzscraper.bandsintown.portal.BitSession;
import sk.drabikp.bzscraper.browser.PlatformBrowser;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.importing.domain.ImportedGig;
import sk.drabikp.bzscraper.sync.application.port.out.FailureKind;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One logged-in artist-portal browser. Works only through the portal's own pages — each one
 * a page object ({@link BitEventsPage}, {@link BitBulkUpload}, {@link BitEventForm},
 * {@link BitDeleteDialog}) — and reads the portal's replies to them ({@link PortalReply}):
 * <ul>
 *   <li><b>create</b> — Bulk Upload of a CSV without event ids (≤ 25 rows) creates drafts,
 *       published with "Notify my followers" as configured; the reply maps each row to its
 *       new event id; the event lists are read back.</li>
 *   <li><b>update</b> — Bulk Upload of rows WITH event ids; past events (which the upload
 *       refuses) through the event's form.</li>
 *   <li><b>delete</b> — the Upcoming row's "⋯" → Delete, or (past events) the form's Delete,
 *       with a reason ("canceled" or "other").</li>
 *   <li><b>list</b> — the Upcoming and Past lists.</li>
 * </ul>
 * A failure leaves a screenshot of the page behind; anything unexpected from the browser is a
 * temporary failure.
 */
final class SeleniumBitSession implements BitSession {

    private static final Logger logger = LoggerFactory.getLogger(SeleniumBitSession.class);

    private final BitPortal portal;
    private final BitEventsPage events;
    private final String artistName;
    private final PlatformBrowser.Lease lease;
    private final Clock clock;
    private boolean closed;

    SeleniumBitSession(PlatformBrowser.Lease lease, BitPortal portal, String artistName, Clock clock) {
        this.lease = lease;
        this.clock = clock;
        this.portal = portal;
        this.events = new BitEventsPage(portal);
        this.artistName = artistName;
    }

    // --- create ---

    @Override
    public List<Created> createEvents(List<Gig> gigs, boolean notifyFollowers) {
        List<Created> results = new ArrayList<>();
        for (int from = 0; from < gigs.size(); from += MAX_ROWS_PER_UPLOAD) {
            List<Gig> batch = gigs.subList(from, Math.min(gigs.size(), from + MAX_ROWS_PER_UPLOAD));
            if (from > 0) {
                portal.longPause();
            }
            try {
                results.addAll(guarded("upload", "Bandsintown upload failed", () -> createBatch(batch, notifyFollowers)));
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
        BitBulkUpload upload = events.bulkUpload();
        PortalReply reply = upload.upload(BandsintownCsv.newEvents(batch, artistName, notifyFollowers));
        if (!reply.ok()) {
            throw reply.rowErrors().isEmpty()
                    ? BitPortalException.temporary("Bandsintown rejected the upload — " + reply.describe())
                    : BitPortalException.refused("Bandsintown refused the events: " + String.join(", ", reply.rowErrors()));
        }
        String[] drafts = reply.draftIds(batch.size());
        String[] existing = reply.updatedIds(batch.size());
        if (Arrays.stream(drafts).anyMatch(Objects::nonNull)) {
            upload.publishDrafts(notifyFollowers);
        } else {
            upload.ok();                            // every row matched an existing event
        }

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
                results.add(Created.failed(gig, FailureKind.NEEDS_USER, "Uploaded to Bandsintown as event " + id + ", but it is not "
                        + "published (" + (event == null ? "not listed" : event.status()) + ") — publish or delete it there."));
            }
        }
        return results;
    }

    /**
     * The listed events by id: the upcoming list, plus the past list when the batch has gigs
     * already played (published past events are listed there, not as upcoming).
     */
    private Map<String, BitEvent> listed(List<Gig> batch) throws BitPortalException {
        Map<String, BitEvent> byId = new HashMap<>();
        events.upcoming().forEach(e -> byId.put(e.id(), e));
        ZonedDateTime now = ZonedDateTime.now(clock);
        if (batch.stream().anyMatch(gig -> gig.schedule().showStart().isBefore(now))) {
            portal.pause();
            events.past().forEach(e -> byId.putIfAbsent(e.id(), e));
        }
        return byId;
    }

    // --- update ---

    /**
     * One upload of rows WITH event ids, one row per edit. Each row is judged on its own, by its
     * position: updated (its id in {@code updated_events}), refused (Bandsintown's row error), or
     * not applied — a reply with errors may hold back the other rows too, so those simply try again.
     */
    @Override
    public List<Edited> updateEvents(List<EventEdit> edits) throws BitPortalException {
        return guarded("update", "Bandsintown upload failed", () -> {
            BitBulkUpload upload = events.bulkUpload();
            PortalReply reply = upload.upload(BandsintownCsv.updates(edits, artistName));
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
                    results.add(Edited.notUpdated(eventId, FailureKind.NEEDS_USER, "Bandsintown made a new draft " + drafts[i]
                            + " instead of editing event " + eventId + " — delete that draft there"));
                } else if (refused[i] != null) {
                    results.add(Edited.notUpdated(eventId, FailureKind.REFUSED, "Bandsintown refused it: " + refused[i]));
                } else if (eventId.equals(updated[i])) {
                    results.add(Edited.updated(eventId));
                } else {
                    results.add(Edited.notUpdated(eventId, FailureKind.TEMPORARY, "not applied — " + reply.describe()));
                }
            }
            if (reply.ok()) {
                upload.ok();
            }
            return results;
        });
    }

    /**
     * The event's single-page form, which also opens for past events: the place from the
     * venue search (the suggestion in the gig's town), then dates, times, name and
     * description; saved. A note when Bandsintown placed it far from the town.
     */
    @Override
    public Optional<String> editEventInForm(String eventId, Gig gig) throws BitPortalException {
        return guarded("form-edit", "Bandsintown's edit form failed", () -> {
            BitEventForm form = BitEventForm.open(portal, eventId).orElseThrow(() ->
                    BitPortalException.temporary("Bandsintown's form for event " + eventId + " didn't open."));
            form.pickVenue(gig);
            form.setSchedule(gig.schedule().showStart(), gig.schedule().showEnd());
            form.setTitle(gig.title());
            if (gig.description() != null) {
                form.setDescription(gig.description());
            }
            PortalReply reply = form.save();
            return reply.event().map(saved -> BitPlaces.check(gig, saved));
        });
    }

    // --- delete ---

    /** The Upcoming row's "⋯" → Delete. A past event isn't there: refused (the form can). */
    @Override
    public void deleteEvent(String eventId, RemovalReason reason) throws BitPortalException {
        guarded("delete", "Bandsintown delete failed", () -> {
            List<BitEvent> upcoming = events.upcoming();
            Optional<BitEvent> event = upcoming.stream().filter(e -> eventId.equals(e.id())).findFirst();
            if (event.isEmpty()) {
                portal.pause();
                if (events.past().stream().anyMatch(e -> eventId.equals(e.id()))) {
                    throw BitPortalException.refused("Bandsintown event " + eventId + " is a past event, which the "
                            + "event list can't remove (its form can).");
                }
                logger.info("Bandsintown event {} is not listed — already removed", eventId);
                return null;
            }
            portal.pause();
            events.delete(event.get(), upcoming).confirm(eventId, reason);
            return null;
        });
    }

    /**
     * The event's form (it opens for past events too) → Delete → the same dialog as the
     * list's. A form that doesn't open for an event neither list shows: already gone.
     */
    @Override
    public void deleteEventInForm(String eventId, RemovalReason reason) throws BitPortalException {
        guarded("form-delete", "Bandsintown's form delete failed", () -> {
            Optional<BitEventForm> form = BitEventForm.open(portal, eventId);
            if (form.isEmpty()) {
                portal.pause();
                if (events.find(eventId).isPresent()) {
                    throw BitPortalException.temporary("Bandsintown's form for event " + eventId + " didn't open — nothing "
                            + "was deleted.");
                }
                logger.info("Bandsintown event {} is not listed — already removed", eventId);
                return null;
            }
            form.get().delete().confirm(eventId, reason);
            return null;
        });
    }

    // --- list (import) ---

    @Override
    public List<ImportedGig> listEvents() throws BitPortalException {
        return guarded("list", "Could not read the Bandsintown events", () -> {
            Map<String, BitEvent> byId = new LinkedHashMap<>();
            events.upcoming().forEach(e -> byId.putIfAbsent(e.id(), e));
            portal.pause();
            events.past().forEach(e -> byId.putIfAbsent(e.id(), e));
            List<ImportedGig> gigs = new ArrayList<>();
            byId.values().forEach(e -> BitEventMapper.toImported(e).ifPresent(gigs::add));
            return gigs;
        });
    }

    /** Hands the browser back (kept open a little for the next session). Never throws. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        lease.keepWarm();
    }

    /** Runs one operation; a failure leaves a screenshot, and a browser error is a temporary failure. */
    private <T> T guarded(String step, String failure, PlatformBrowser.Work<T, BitPortalException> work)
            throws BitPortalException {
        return lease.guarded(step, failure, SeleniumBitPortalClient.FAILURES, work);
    }
}
