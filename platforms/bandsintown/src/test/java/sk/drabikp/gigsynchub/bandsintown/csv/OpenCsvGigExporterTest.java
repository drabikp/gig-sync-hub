package sk.drabikp.gigsynchub.bandsintown.csv;

import com.opencsv.CSVReader;
import org.junit.jupiter.api.Test;
import sk.drabikp.gigsynchub.accounts.application.AccountsFakes;
import sk.drabikp.gigsynchub.bandsintown.BandsintownPlatform;
import sk.drabikp.gigsynchub.gig.application.GigErrorCode;
import sk.drabikp.gigsynchub.gig.application.UserFacingException;
import sk.drabikp.gigsynchub.gig.domain.Admission;
import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.gig.domain.Country;
import sk.drabikp.gigsynchub.gig.domain.Gig;
import sk.drabikp.gigsynchub.gig.domain.GigSchedule;
import sk.drabikp.gigsynchub.gig.domain.Location;

import java.io.StringReader;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenCsvGigExporterTest {

    private static final String[] EXPECTED_HEADER = {
            "Artist Name", "Venue*", "Country*", "Address", "City*", "Region*", "Postal Code", "Timezone*",
            "Start Date* (yyyy-mm-dd)", "Start Time* (HH:MM)", "End Date", "End Time", "Streaming Link",
            "Ticket Link", "Ticket Type", "Ticket Link 2", "Ticket Type 2", "On-Sale Date", "On-Sale Time",
            "Lineup", "Event Name", "Event Display Format", "Description", "Schedule Date", "Schedule Time",
            "Do Not Announce", "Setlist", "Event Image"
    };

    private final OpenCsvGigExporter exporter = new OpenCsvGigExporter(new AccountsFakes.Accounts()
            .with(BandId.FIRST, BandsintownPlatform.PLATFORM, Map.of("artistName", "Eufory (Band)"), Map.of()));

    private static List<String[]> parse(String csv) throws Exception {
        try (CSVReader reader = new CSVReader(new StringReader(csv))) {
            return reader.readAll();
        }
    }

    private static ZonedDateTime prague(int y, int m, int d, int h, int min) {
        return ZonedDateTime.of(y, m, d, h, min, 0, 0, ZoneId.of("Europe/Prague"));
    }

    @Test
    void each_band_exports_as_its_own_artist_and_one_without_an_artist_name_cant() {
        BandId other = new BandId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        OpenCsvGigExporter twoBands = new OpenCsvGigExporter(new AccountsFakes.Accounts()
                .with(other, BandsintownPlatform.PLATFORM, Map.of("artistName", "Other Band"), Map.of()));

        assertThat(twoBands.export(other, List.of())).isNotBlank();
        assertThatThrownBy(() -> twoBands.export(BandId.FIRST, List.of()))
                .isInstanceOfSatisfying(UserFacingException.class,
                        e -> assertThat(e.code()).isEqualTo(GigErrorCode.NOT_CONFIGURED));
    }

    @Test
    void header_matches_bandsintown_template_exactly() throws Exception {
        List<String[]> rows = parse(exporter.export(BandId.FIRST, List.of()));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsExactly(EXPECTED_HEADER);
        assertThat(rows.get(0)[7]).isEqualTo("Timezone*");
    }

    @Test
    void czech_gig_maps_country_timezone_ticket_description_image_and_core_fields() throws Exception {
        Gig g = Gig.create("The Legends Rock Fest", GigSchedule.startingAt(prague(2026, 9, 15, 20, 0)),
                new Location("Klub 007", "Hořice", Country.CZECHIA), List.of("Eufory", "Support"),
                Admission.paid("150 Kč"), "A fun night",
                null, "https://tickets.example/legends", "https://img.example/poster.jpg");

        String[] row = parse(exporter.export(BandId.FIRST, List.of(g))).get(1);

        assertThat(row[0]).isEqualTo("Eufory (Band)");
        assertThat(row[1]).isEqualTo("Klub 007");
        assertThat(row[2]).isEqualTo("Czechia");
        assertThat(row[4]).isEqualTo("Hořice");
        assertThat(row[7]).isEqualTo("Europe/Prague");
        assertThat(row[8]).isEqualTo("2026-09-15");
        assertThat(row[9]).isEqualTo("20:00");
        assertThat(row[13]).isEqualTo("https://tickets.example/legends");
        assertThat(row[14]).isEqualTo("Tickets");
        assertThat(row[19]).isEqualTo("Eufory,Support");
        assertThat(row[20]).isEqualTo("The Legends Rock Fest");
        assertThat(row[22]).isEqualTo("A fun night");
        assertThat(row[27]).isEqualTo("https://img.example/poster.jpg");
    }

    @Test
    void slovak_gig_maps_country_timezone_and_defaults_missing_venue_to_TBA() throws Exception {
        Gig g = Gig.create("CityFest MT", GigSchedule.startingAt(prague(2026, 8, 21, 19, 30)),
                new Location(null, "Martin", Country.SLOVAKIA), List.of(), Admission.free(),
                null, null, null, null);

        String[] row = parse(exporter.export(BandId.FIRST, List.of(g))).get(1);

        assertThat(row[1]).isEqualTo("TBA");
        assertThat(row[2]).isEqualTo("Slovakia");
        assertThat(row[4]).isEqualTo("Martin");
        assertThat(row[7]).isEqualTo("Europe/Bratislava");
        assertThat(row[9]).isEqualTo("19:30");
    }

    @Test
    void unknown_country_yields_blank_country_and_timezone() throws Exception {
        Gig g = Gig.create("Somewhere", GigSchedule.startingAt(prague(2026, 7, 1, 20, 0)),
                new Location("Hall", "Vienna", null), List.of(), Admission.free(), null, null, null, null);

        String[] row = parse(exporter.export(BandId.FIRST, List.of(g))).get(1);

        assertThat(row[2]).isEmpty();
        assertThat(row[7]).isEmpty();
    }

    @Test
    void no_ticket_link_leaves_ticket_type_blank() throws Exception {
        Gig g = Gig.create("Free show", GigSchedule.startingAt(prague(2026, 9, 1, 20, 0)),
                new Location("Klub", "Praha", Country.CZECHIA), List.of(), Admission.free(), null, null, null, null);

        String[] row = parse(exporter.export(BandId.FIRST, List.of(g))).get(1);

        assertThat(row[13]).isEmpty();
        assertThat(row[14]).isEmpty();
    }

    @Test
    void end_datetime_is_emitted_when_present() throws Exception {
        Gig g = Gig.create("Festival",
                new GigSchedule(prague(2026, 8, 27, 18, 0), prague(2026, 8, 30, 23, 0)),
                new Location("Camp", "Stará Turá", Country.SLOVAKIA), List.of(), Admission.free(),
                null, null, null, null);

        String[] row = parse(exporter.export(BandId.FIRST, List.of(g))).get(1);

        assertThat(row[10]).isEqualTo("2026-08-30");
        assertThat(row[11]).isEqualTo("23:00");
    }
}
