package sk.drabikp.bzscraper.bandsintown.portal.selenium;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import sk.drabikp.bzscraper.bandsintown.BitPortalException;
import sk.drabikp.bzscraper.bandsintown.RemovalReason;
import sk.drabikp.bzscraper.gig.domain.Admission;
import sk.drabikp.bzscraper.gig.domain.Country;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.GigSchedule;
import sk.drabikp.bzscraper.gig.domain.Location;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The Bandsintown page objects against copies of the portal's pages ({@link PortalFixture}):
 * what they read from the portal's replies, and that they touch the right controls — the row
 * that was checked, the dialog's own reason, details and Delete, the place in the gig's town.
 */
class BitPortalPagesTest {

    private PortalFixture portal;

    @BeforeEach
    void start() throws Exception {
        portal = new PortalFixture();
    }

    @AfterEach
    void stop() {
        if (portal != null) {
            portal.close();
        }
    }

    private static String event(long id, String date, String venue, String city) {
        return "{\"id\":" + id + ",\"status\":\"PUBLISHED\",\"start_date\":\"" + date + "\",\"start_time\":\"20:00:00\","
                + "\"venue_name\":\"" + venue + "\",\"venue_city\":\"" + city + "\",\"venue_country\":\"Slovakia\"}";
    }

    private static String list(String... events) {
        return "{\"status\":\"OK\",\"payload\":[" + String.join(",", events) + "]}";
    }

    private static final String SNP = event(102, "2026-11-28", "Secret Garden", "Košice");

    @Test
    void the_lists_come_from_the_portals_own_replies_and_the_past_one_page_by_page() throws Exception {
        portal.reply("/api/managed-actors/1/events?past=false", list(event(101, "2026-11-20", "Randal", "Bratislava"), SNP))
                .reply("/api/managed-actors/1/events?past=true&page=1", list(event(90, "2026-08-29", "A", "Martin")), "2")
                .reply("/api/managed-actors/1/events?past=true&page=2", list(event(91, "2026-07-18", "B", "Hořice")));
        BitEventsPage events = new BitEventsPage(portal.portal);

        assertThat(events.upcoming()).extracting(BitEvent::id, BitEvent::venueCity)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("101", "Bratislava"),
                        org.assertj.core.groups.Tuple.tuple("102", "Košice"));
        assertThat(events.past().stream().map(BitEvent::id).toList()).containsExactly("90", "91");
        assertThat(portal.writes()).isEmpty();
    }

    @Test
    void the_row_menu_removes_the_checked_event_with_the_reason_and_a_detail() throws Exception {
        portal.reply("/api/managed-actors/1/events?past=false", list(event(101, "2026-11-20", "Randal", "Bratislava"), SNP))
                .reply("/api/managed-actors/1/events/102", "{\"status\":\"OK\",\"payload\":{\"id\":102,\"status\":\"DELETED\"}}");
        BitEventsPage events = new BitEventsPage(portal.portal);
        List<BitEvent> listed = events.upcoming();

        events.delete(listed.get(1), listed).confirm("102", RemovalReason.CANCELED);

        assertThat(portal.writes()).singleElement().satisfies(write -> {
            assertThat(write.method()).isEqualTo("PATCH");
            assertThat(write.path()).isEqualTo("/api/managed-actors/1/events/102");
            assertThat(write.body()).contains("\"reason\":\"CANCELED\"", "The concert was cancelled.");
        });
    }

    @Test
    void a_row_that_isnt_what_the_list_said_is_left_alone() throws Exception {
        portal.reply("/api/managed-actors/1/events?past=false", list(event(101, "2026-11-20", "Randal", "Bratislava"), SNP));
        BitEventsPage events = new BitEventsPage(portal.portal);
        List<BitEvent> listed = events.upcoming();
        BitEvent elsewhere = new BitEvent("102", "PUBLISHED", "2026-11-28", null, null, null, null, null, List.of(), null,
                "Secret Garden", "Praha", "Czechia", null, null, null);

        assertThatThrownBy(() -> events.delete(elsewhere, List.of(listed.get(0), elsewhere)))
                .isInstanceOf(BitPortalException.class).hasMessageContaining("nothing was deleted");
        assertThat(portal.writes()).isEmpty();
    }

    @Test
    void the_form_picks_the_place_in_the_gigs_town_and_saves_it() throws Exception {
        portal.reply("/api/venues/autocomplete", "{\"status\":\"OK\",\"payload\":["
                        + "{\"place_id\":\"ChIJbratislava\",\"name\":\"Secret Garden\",\"description\":\"Secret Garden, Obchodná, Bratislava, Slovakia\"},"
                        + "{\"place_id\":\"ChIJkosice\",\"name\":\"Secret Garden (Skrytý Dvor)\",\"description\":\"Secret Garden (Skrytý Dvor), Moyzesova, Košice, Slovakia\"}]}")
                .reply("/api/managed-actors/1/events/109", "{\"status\":\"OK\",\"payload\":{\"id\":109,\"status\":\"PUBLISHED\","
                        + "\"venue_latitude\":48.7214,\"venue_longitude\":21.2525}}");
        Gig gig = Gig.create("SNP - Spolu Na Pódiu Košice",
                GigSchedule.startingAt(ZonedDateTime.of(2026, 8, 29, 21, 0, 0, 0, ZoneId.of("Europe/Bratislava"))),
                new Location("Secret Garden (Skrytý Dvor)", "Košice", Country.SLOVAKIA), List.of(), Admission.free(),
                null, null, null, null);
        BitEventForm form = BitEventForm.open(portal.portal, "109").orElseThrow();

        form.pickVenue(gig);
        form.setTitle(gig.title());
        PortalReply saved = form.save();

        assertThat(portal.writes()).singleElement().satisfies(write -> {
            assertThat(write.path()).isEqualTo("/api/managed-actors/1/events/109");
            assertThat(write.body()).contains("\"venue\":\"Secret Garden (Skrytý Dvor), Moyzesova, Košice, Slovakia\"",
                    "\"title\":\"SNP - Spolu Na Pódiu Košice\"");
        });
        assertThat(saved.event()).get().extracting(BitEvent::latitude).isEqualTo(48.7214);
    }

    @Test
    void the_forms_delete_is_confirmed_inside_the_dialog_not_with_the_forms_own_controls() throws Exception {
        portal.reply("/api/managed-actors/1/events/109", "{\"status\":\"OK\",\"payload\":{\"id\":109,\"status\":\"DELETED\"}}");

        BitEventForm.open(portal.portal, "109").orElseThrow().delete().confirm("109", RemovalReason.OTHER);

        assertThat(portal.writes()).singleElement().satisfies(write ->
                assertThat(write.body()).contains("\"status\":\"DELETED\"", "\"reason\":\"OTHER\"", "Removed by the band."));
        assertThat(portal.driver.findElement(By.name("presale")).getDomProperty("value"))
                .as("the form's own text area is untouched").isEqualTo("presale notes");
    }

    @Test
    void an_event_without_a_form_has_none() {
        assertThat(BitEventForm.open(portal.portal, "gone")).isEmpty();
    }
}
