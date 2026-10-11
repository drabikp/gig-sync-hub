package sk.drabikp.bzscraper.bandsintown.csv;

import com.opencsv.CSVReader;
import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.bandsintown.portal.BitSession.EventEdit;
import sk.drabikp.bzscraper.gig.domain.Address;
import sk.drabikp.bzscraper.gig.domain.Country;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.gig.domain.GigSchedule;
import sk.drabikp.bzscraper.gig.domain.Location;
import sk.drabikp.bzscraper.gig.domain.Slot;
import sk.drabikp.bzscraper.gig.domain.TestGigs;

import java.io.StringReader;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BandsintownCsvTest {

    private final Gig gig = TestGigs.gig("Fest", "Klub 007");

    private static List<String[]> parse(String csv) throws Exception {
        try (CSVReader reader = new CSVReader(new StringReader(csv))) {
            return reader.readAll();
        }
    }

    @Test
    void new_events_use_the_28_template_columns() throws Exception {
        List<String[]> rows = parse(BandsintownCsv.newEvents(List.of(gig), "Eufory (Band)", true));

        assertThat(rows.get(0)).containsExactly(BandsintownCsv.header(BandsintownCsv.Column.TEMPLATE));
        assertThat(rows.get(1)).hasSize(28);
        assertThat(rows.get(1)[0]).isEqualTo("Eufory (Band)");
        assertThat(rows.get(1)[20]).isEqualTo("Fest");
    }

    @Test
    void new_events_are_marked_do_not_announce_unless_followers_should_be_notified() throws Exception {
        assertThat(parse(BandsintownCsv.newEvents(List.of(gig), "X", false)).get(1)[25]).isEqualTo("Y");
        assertThat(parse(BandsintownCsv.newEvents(List.of(gig), "X", true)).get(1)[25]).isEmpty();
    }

    @Test
    void updates_append_event_id_and_status_and_are_never_announced() throws Exception {
        List<String[]> rows = parse(BandsintownCsv.updates(List.of(new EventEdit("109006286", gig)), "Eufory (Band)"));

        assertThat(rows.get(0)).hasSize(30);
        assertThat(rows.get(0)[28]).isEqualTo("Event Id");
        assertThat(rows.get(0)[29]).isEqualTo("Status");
        assertThat(rows.get(1)[28]).isEqualTo("109006286");
        assertThat(rows.get(1)[29]).isEqualTo("PUBLISHED");
        assertThat(rows.get(1)[25]).isEqualTo("Y");
    }

    @Test
    void the_download_template_leaves_do_not_announce_blank() throws Exception {
        assertThat(parse(BandsintownCsv.template(List.of(gig), "X")).get(1)[25]).isEmpty();
    }

    @Test
    void a_festival_with_the_bands_slot_is_listed_at_the_slot_otherwise_at_the_event() throws Exception {
        ZoneId zone = ZoneId.of("Europe/Bratislava");
        GigSchedule festival = new GigSchedule(ZonedDateTime.of(2026, 8, 27, 12, 0, 0, 0, zone),
                ZonedDateTime.of(2026, 8, 30, 0, 0, 0, 0, zone));
        Gig withSlot = new Gig(GigId.fresh(), "Moto Fest", festival.withSlot(new Slot(ZonedDateTime.of(2026, 8, 28, 19, 30, 0, 0, zone),
                ZonedDateTime.of(2026, 8, 28, 20, 45, 0, 0, zone))), gig.location(), List.of(), gig.admission(),
                null, null, null, null, false);
        Gig withoutSlot = new Gig(GigId.fresh(), "Moto Fest", festival, gig.location(), List.of(), gig.admission(),
                null, null, null, null, false);

        assertThat(Arrays.copyOfRange(parse(BandsintownCsv.newEvents(List.of(withSlot), "X", false)).get(1), 8, 12))
                .containsExactly("2026-08-28", "19:30", "2026-08-28", "20:45");
        assertThat(Arrays.copyOfRange(parse(BandsintownCsv.newEvents(List.of(withoutSlot), "X", false)).get(1), 8, 12))
                .containsExactly("2026-08-27", "12:00", "2026-08-30", "00:00");
    }

    @Test
    void street_postal_code_and_region_go_along_so_bandsintown_finds_the_right_town() throws Exception {
        Gig located = new Gig(GigId.fresh(), "Fest", gig.schedule(), new Location("Zámecký klub", "Hranice", Country.CZECHIA,
                new Address("Pernštejnské nám. 1", "753 01", "okres Přerov", "Olomoucký kraj", 49.548, 17.735)),
                List.of(), gig.admission(), null, null, null, null, false);

        String[] row = parse(BandsintownCsv.newEvents(List.of(located), "X", false)).get(1);

        assertThat(row[3]).isEqualTo("Pernštejnské nám. 1");
        assertThat(row[4]).isEqualTo("Hranice");
        assertThat(row[5]).isEqualTo("Olomoucký kraj");
        assertThat(row[6]).isEqualTo("753 01");
        assertThat(parse(BandsintownCsv.newEvents(List.of(gig), "X", false)).get(1)[5]).isEmpty();
        assertThat(parse(BandsintownCsv.newEvents(List.of(gig), "X", false)).get(1)[6]).isEmpty();
    }

    @Test
    void the_columns_come_in_the_templates_order_and_an_update_appends_event_id_and_status() throws Exception {
        String[] template = {"Artist Name", "Venue*", "Country*", "Address", "City*", "Region*", "Postal Code",
                "Timezone*", "Start Date* (yyyy-mm-dd)", "Start Time* (HH:MM)", "End Date", "End Time", "Streaming Link",
                "Ticket Link", "Ticket Type", "Ticket Link 2", "Ticket Type 2", "On-Sale Date", "On-Sale Time", "Lineup",
                "Event Name", "Event Display Format", "Description", "Schedule Date", "Schedule Time", "Do Not Announce",
                "Setlist", "Event Image"};

        assertThat(parse(BandsintownCsv.newEvents(List.of(gig), "X", true)).get(0)).containsExactly(template);
        assertThat(parse(BandsintownCsv.updates(List.of(), "X")).get(0)).startsWith(template)
                .endsWith("Event Id", "Status").hasSize(30);
    }

    @Test
    void every_edit_is_a_row_of_its_own_even_two_of_one_event() throws Exception {
        Gig other = TestGigs.gig("Other", "Barrák");

        List<String[]> rows = parse(BandsintownCsv.updates(List.of(new EventEdit("101", gig), new EventEdit("101", other)),
                "X"));

        assertThat(rows).hasSize(3);
        assertThat(rows.get(1)[20]).isEqualTo("Fest");
        assertThat(rows.get(2)[20]).isEqualTo("Other");
    }
}
