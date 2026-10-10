package sk.drabikp.bzscraper.bandsintown.portal.http;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.bandsintown.BitPortalException;
import sk.drabikp.bzscraper.bandsintown.RemovalReason;
import sk.drabikp.bzscraper.bandsintown.portal.BitSession;
import sk.drabikp.bzscraper.gig.domain.Address;
import sk.drabikp.bzscraper.gig.domain.Admission;
import sk.drabikp.bzscraper.gig.domain.Country;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.GigSchedule;
import sk.drabikp.bzscraper.gig.domain.Location;
import sk.drabikp.bzscraper.gig.domain.TestGigs;
import sk.drabikp.bzscraper.importing.domain.ImportedGig;
import sk.drabikp.bzscraper.sync.application.port.out.FailureKind;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The HTTP session against a stand-in that answers as the artist portal's API does
 * ({@link BitHttpFixture}): publishing and its read-back, the paged lists, an update by event id, the
 * venue pick and a removal — and that a session the portal no longer accepts is the user's to renew.
 */
class HttpBitSessionTest {

    private static final ZoneId PRAGUE = ZoneId.of("Europe/Prague");
    private static final Clock TODAY = Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), PRAGUE);

    private BitHttpFixture portal;
    private BitSession session;

    @BeforeEach
    void setUp() throws IOException {
        portal = new BitHttpFixture();
        session = new HttpBitSession(new BitWeb(portal.baseUrl(), portal.credentials()), "Eufory (Band)", TODAY);
    }

    @AfterEach
    void tearDown() {
        portal.close();
    }

    @Test
    void publishes_a_batch_and_reads_back_that_each_event_really_is_published() {
        List<Gig> gigs = List.of(TestGigs.gig("Release show", "Lucerna Music Bar", at(2026, 11, 12)),
                TestGigs.gig("Club night", "Futurum", at(2026, 11, 20)));

        List<BitSession.Created> created = session.createEvents(gigs, false);

        assertThat(created).hasSize(2).allSatisfy(one -> assertThat(one.isPublished()).isTrue());
        assertThat(created).extracting(BitSession.Created::eventId).doesNotContainNull();
        assertThat(portal.events.values()).extracting(event -> event.get("status"))
                .containsOnly("PUBLISHED");
        assertThat(portal.uploads).hasSize(1);
        assertThat(portal.uploads.getFirst()).as("the artist, and followers not told of it")
                .contains("Eufory (Band)").contains("\"Y\"");
    }

    @Test
    void a_gig_already_played_is_looked_for_among_the_past_events_too() {
        List<BitSession.Created> created = session.createEvents(
                List.of(TestGigs.gig("Last month", "Lucerna Music Bar", at(2026, 9, 12))), true);

        assertThat(created).singleElement().satisfies(one -> {
            assertThat(one.isPublished()).as(one.error()).isTrue();
            assertThat(portal.events.get(one.eventId()).get("status")).isEqualTo("PUBLISHED");
        });
    }

    @Test
    void a_row_bandsintown_refuses_fails_that_batch_without_trying_again() {
        portal.refusedRow = 2;

        List<BitSession.Created> created = session.createEvents(
                List.of(TestGigs.gig("Too late", "Lucerna Music Bar", at(2026, 11, 12))), false);

        assertThat(created).singleElement().satisfies(one -> {
            assertThat(one.isPublished()).isFalse();
            assertThat(one.failure()).isEqualTo(FailureKind.REFUSED);
            assertThat(one.error()).contains("INVALID_START_TIME");
        });
    }

    @Test
    void a_draft_bandsintown_wont_publish_is_left_there_for_the_user() {
        portal.publishFails = true;

        List<BitSession.Created> created = session.createEvents(
                List.of(TestGigs.gig("Stuck", "Lucerna Music Bar", at(2026, 11, 12))), false);

        assertThat(created).singleElement().satisfies(one -> {
            assertThat(one.failure()).isEqualTo(FailureKind.NEEDS_USER);
            assertThat(one.error()).contains("not published").contains("publish or delete it there");
        });
        assertThat(portal.events.values()).extracting(event -> event.get("status")).containsOnly("DRAFT");
    }

    @Test
    void the_past_events_are_read_page_by_page() throws Exception {
        portal.pastPageSize = 4;
        for (int i = 0; i < 9; i++) {
            portal.event("PUBLISHED", "2026-0" + (i + 1) + "-05", Map.of());
        }
        portal.event("PUBLISHED", "2026-12-01", Map.of());

        List<ImportedGig> imported = session.listEvents();

        assertThat(imported).as("every past page, and the upcoming one").hasSize(10);
        assertThat(imported).extracting(ImportedGig::externalRef).doesNotHaveDuplicates();
    }

    @Test
    void an_update_carrying_the_event_id_edits_that_event() throws Exception {
        String id = portal.event("PUBLISHED", "2026-11-12", Map.of("title", "Old name"));

        List<BitSession.Edited> edited = session.updateEvents(List.of(new BitSession.EventEdit(id,
                TestGigs.gig("New name", "Lucerna Music Bar", at(2026, 11, 12)))));

        assertThat(edited).singleElement().satisfies(one -> assertThat(one.isUpdated()).as(one.error()).isTrue());
        assertThat(portal.events.get(id).get("title")).isEqualTo("New name");
    }

    @Test
    void a_row_bandsintown_turns_into_a_new_draft_needs_the_user() throws Exception {
        String id = portal.event("PUBLISHED", "2026-11-12", Map.of());
        portal.updatesMakeDrafts = true;

        List<BitSession.Edited> edited = session.updateEvents(List.of(new BitSession.EventEdit(id,
                TestGigs.gig("New name", "Lucerna Music Bar", at(2026, 11, 12)))));

        assertThat(edited).singleElement().satisfies(one -> {
            assertThat(one.failure()).isEqualTo(FailureKind.NEEDS_USER);
            assertThat(one.error()).contains("made a new draft").contains("delete that draft");
        });
    }

    @Test
    void one_events_own_fields_change_and_its_place_is_left_alone() throws Exception {
        String id = portal.event("PUBLISHED", "2026-11-12",
                Map.of("venue_name", "Lucerna Music Bar", "venue_city", "Praha"));

        session.editEventInForm(id, inPraha("Garden show", "Lucerna Music Bar"));

        assertThat(portal.changes).last().satisfies(change -> {
            assertThat(change.getKey()).isEqualTo(id);
            assertThat(change.getValue()).contains("\"title\":\"Garden show\"").contains("\"status\":\"PUBLISHED\"")
                    .as("the place is never sent: Bandsintown only ever times out on it")
                    .doesNotContain("venue");
        });
        assertThat(portal.events.get(id).get("title")).isEqualTo("Garden show");
    }

    @Test
    void moving_one_events_place_needs_the_user_and_changes_nothing() throws Exception {
        String id = portal.event("PUBLISHED", "2026-11-12",
                Map.of("venue_name", "Lucerna Music Bar", "venue_city", "Praha"));

        assertThatThrownBy(() -> session.editEventInForm(id, inPraha("Garden show", "Secret Garden")))
                .isInstanceOfSatisfying(BitPortalException.class,
                        e -> assertThat(e.kind()).isEqualTo(FailureKind.NEEDS_USER));
        assertThat(portal.changes).as("nothing was sent").isEmpty();
    }

    /** A gig in Praha whose town was picked from the place search, so its whereabouts are known. */
    private static Gig inPraha(String title, String venue) {
        return Gig.create(title, GigSchedule.startingAt(at(2026, 11, 12)),
                new Location(venue, "Praha", Country.CZECHIA,
                        new Address(null, "110 00", "okres Praha", "Praha", 50.0755, 14.4378)),
                List.of(), Admission.free(), null, null, null, null);
    }

    private static ZonedDateTime at(int year, int month, int day) {
        return ZonedDateTime.of(year, month, day, 20, 0, 0, 0, PRAGUE);
    }
}
