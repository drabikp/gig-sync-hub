package sk.drabikp.gigsynchub.bandsintown.portal.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import sk.drabikp.gigsynchub.bandsintown.BitPortalException;
import sk.drabikp.gigsynchub.bandsintown.RemovalReason;
import sk.drabikp.gigsynchub.bandsintown.portal.BitEvent;
import sk.drabikp.gigsynchub.bandsintown.portal.PortalReply;
import sk.drabikp.gigsynchub.bandsintown.portal.BitSession;
import sk.drabikp.gigsynchub.gig.domain.Address;
import sk.drabikp.gigsynchub.gig.domain.Admission;
import sk.drabikp.gigsynchub.gig.domain.Country;
import sk.drabikp.gigsynchub.gig.domain.Gig;
import sk.drabikp.gigsynchub.gig.domain.GigId;
import sk.drabikp.gigsynchub.gig.domain.GigSchedule;
import sk.drabikp.gigsynchub.gig.domain.Location;
import sk.drabikp.gigsynchub.importing.domain.ImportedGig;
import sk.drabikp.gigsynchub.sync.application.port.out.FailureKind;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The HTTP client against the REAL artist portal, with NO BROWSER: from the connection one browser sign-in
 * left behind ({@code BIT_CONNECTION_FILE}, as the snippet in Settings prints it) it signs in, CREATES a
 * made-up event without announcing it, reads it back, edits it both ways the sync does, then REMOVES it —
 * the removal runs even when a step fails, and removing it twice must be a no-op.
 *
 * <p>Every step is tried and reported, so one made-up event tells us about all of them; the test fails at
 * the end if any did. Skipped unless {@code BIT_LIVE=true}. The event is public for about a minute.
 */
@EnabledIfEnvironmentVariable(named = "BIT_LIVE", matches = "true")
class HttpBitPortalClientLiveTest {

    private static final ZoneId BRATISLAVA = ZoneId.of("Europe/Bratislava");

    private static String env(String name, String fallback) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? fallback : v;
    }

    @Test
    void creates_reads_edits_and_removes_a_real_event_without_a_browser() throws Exception {
        String baseUrl = env("BIT_BASE_URL", "https://artists.bandsintown.com");
        String artistName = env("BIT_ARTIST_NAME", "");
        BitConnection connection = BitConnection.read(
                Files.readString(Path.of(env("BIT_CONNECTION_FILE", "/dev/null"))));
        BitApi api = new BitWeb(baseUrl, new BitSignIn(baseUrl, connection, Clock.systemUTC()));
        BitSession session = new HttpBitSession(api, artistName, Clock.systemDefaultZone());

        Gig gig = new Gig(GigId.fresh(), "GIGSYNCHUB TEST - please ignore",
                GigSchedule.startingAt(ZonedDateTime.of(2027, 10, 7, 20, 0, 0, 0, BRATISLAVA)),
                new Location("GIGSYNCHUB TEST VENUE", "Bratislava", Country.SLOVAKIA), List.of(),
                Admission.free(), "Automated test event - will be deleted.", null, null, null, false);
        Gig byUpload = new Gig(GigId.fresh(), "GIGSYNCHUB TEST - please ignore (edited)",
                GigSchedule.startingAt(ZonedDateTime.of(2027, 10, 7, 21, 30, 0, 0, BRATISLAVA)),
                new Location("GIGSYNCHUB TEST VENUE", "Bratislava", Country.SLOVAKIA), List.of(),
                Admission.free(), "Automated test event - edited by upload.", null, null, null, false);
        Gig byForm = new Gig(GigId.fresh(), "GIGSYNCHUB TEST - please ignore (form)",
                GigSchedule.startingAt(ZonedDateTime.of(2027, 10, 8, 19, 15, 0, 0, BRATISLAVA)),
                new Location("Majestic Music Club", "Bratislava", Country.SLOVAKIA,
                        new Address(null, "811 01", null, "Bratislavský kraj", 48.1486, 17.1077)), List.of(),
                Admission.free(), "Automated test event - edited in the form.", null, null, null, false);

        Gig typedElsewhere = new Gig(GigId.fresh(), "GIGSYNCHUB TEST - please ignore (typed town)",
                GigSchedule.startingAt(ZonedDateTime.of(2027, 10, 8, 19, 15, 0, 0, BRATISLAVA)),
                new Location("Somewhere Else", "Košice", Country.SLOVAKIA), List.of(),
                Admission.free(), "Automated test event - a town nobody looked up.", null, null, null, false);

        Gig samePlace = new Gig(GigId.fresh(), "GIGSYNCHUB TEST - please ignore (same place)",
                GigSchedule.startingAt(ZonedDateTime.of(2027, 10, 8, 19, 15, 0, 0, BRATISLAVA)),
                gig.location(), List.of(), Admission.free(), "Automated test event - time changed.",
                null, null, null, false);

        Map<String, String> steps = new LinkedHashMap<>();
        String id = null;
        try {
            List<BitSession.Created> created = session.createEvents(List.of(gig), false);
            BitSession.Created one = created.getFirst();
            id = one.eventId();
            steps.put("1 create + publish silently", one.isPublished() ? "ok, event " + id : "FAILED: " + one.error());

            if (id != null) {
                try {
                    List<String> listed = new ArrayList<>();
                    for (ImportedGig event : session.listEvents()) {
                        if (event.externalRef().equals(id)) {
                            listed.add(event.gig().schedule().start() + " " + event.gig().location().venue());
                        }
                    }
                    steps.put("2 read back from the list", listed.isEmpty() ? "FAILED: not listed" : "ok, " + listed);
                } catch (RuntimeException | Error e) {
                    steps.put("2 read back from the list", "FAILED: " + e);
                } catch (Exception e) {
                    steps.put("2 read back from the list", "FAILED: " + e.getMessage());
                }
                String eventId = id;
                // which part of a one-event change the portal struggles with: its own fields, or the venue
                steps.put("3a change the title alone", tried(() -> {
                    BitEvent stored = api.get(api.actorPath("events/" + eventId)).event().orElseThrow();
                    PortalReply reply = api.patch(api.actorPath("events/" + eventId), Map.of(
                            "version", stored.version(), "status", stored.status(), "title", "GIGSYNCHUB TEST - title"));
                    return reply.ok() ? "ok" : "FAILED: " + reply.describe();
                }));
                steps.put("3 edit by upload", tried(() -> {
                    BitSession.Edited edited = session.updateEvents(
                            List.of(new BitSession.EventEdit(eventId, byUpload))).getFirst();
                    return edited.isUpdated() ? "ok" : "FAILED: " + edited.error();
                }));
                String formId = id;
                steps.put("4 change one event, its place unchanged", tried(() ->
                        "ok" + session.editEventInForm(formId, samePlace).map(note -> ", note: " + note).orElse("")));
                steps.put("5 move its place (the PUT the portal's own form sends)", tried(() -> {
                    String note = session.editEventInForm(formId, byForm).orElse("");
                    BitEvent saved = api.get(api.actorPath("events/" + eventId)).event().orElseThrow();
                    if (!"Majestic Music Club".equals(saved.venueName())) {
                        return "FAILED: it saved the venue as " + saved.venueName();
                    }
                    if (saved.latitude() == null || Math.abs(saved.latitude() - 48.1486) > 0.5) {
                        return "FAILED: it saved the whereabouts as " + saved.latitude() + "," + saved.longitude();
                    }
                    return "ok, " + saved.venueName() + " at " + saved.latitude() + "," + saved.longitude()
                            + (note.isEmpty() ? "" : " (note: " + note + ")");
                }));
                steps.put("5b a town only typed in still needs the user", tried(() -> {
                    try {
                        session.editEventInForm(formId, typedElsewhere);
                        return "FAILED: it moved the place without knowing where it is";
                    } catch (BitPortalException e) {
                        return e.kind() == FailureKind.NEEDS_USER ? "ok, needs the user" : "FAILED: " + e.kind();
                    }
                }));
            }
        } finally {
            if (id != null) {
                String remove = id;
                steps.put("6 remove it", tried(() -> {
                    session.deleteEvent(remove, RemovalReason.CANCELED);
                    return "ok";
                }));
                steps.put("7 remove it again (must be a no-op)", tried(() -> {
                    session.deleteEvent(remove, RemovalReason.CANCELED);
                    return "ok";
                }));
                steps.put("8 gone from the lists", tried(() -> session.listEvents().stream()
                        .noneMatch(e -> e.externalRef().equals(remove)) ? "ok" : "FAILED: still listed"));
            }
            session.close();
            steps.forEach((step, outcome) -> System.out.println("BIT HTTP " + step + ": " + outcome));
        }
        assertThat(steps.values()).as("every step").noneMatch(outcome -> outcome.startsWith("FAILED"));
    }

    /** Runs a step and says how it went, so one made-up event reports on them all. */
    private static String tried(Step step) {
        try {
            return step.run();
        } catch (Exception e) {
            return "FAILED: " + (e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private interface Step {
        String run() throws Exception;
    }
}
