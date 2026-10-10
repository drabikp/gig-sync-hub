package sk.drabikp.bzscraper.bandsintown.portal.selenium;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.bandsintown.BandsintownPlatform;
import sk.drabikp.bzscraper.gig.domain.Country;
import sk.drabikp.bzscraper.importing.domain.ImportedGig;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Events shaped like the artist portal's event list (fields as captured live, 2026-10-07). */
class BitEventMapperTest {

    private static Map<String, Object> event() {
        Map<String, Object> e = new HashMap<>();
        e.put("id", 108115231L);
        e.put("status", "PUBLISHED");
        e.put("title", "MOTOSRAZ HROMOVICA 2026");
        e.put("use_custom_title", true);
        e.put("headline_artist_name", "Eufory (Band)");
        e.put("venue_name", "Auto Camping Dubník");
        e.put("venue_city", "Stará Turá");
        e.put("venue_country", "Slovakia");
        e.put("venue_timezone", "Europe/Bratislava");
        e.put("start_date", "2026-08-27");
        e.put("start_time", "00:00:00");
        e.put("end_date", "2026-08-30");
        e.put("end_time", "00:00:00");
        e.put("description", null);
        e.put("lineup", List.of());
        return e;
    }

    @Test
    void maps_a_published_event_with_its_id() {
        ImportedGig imported = BitEventMapper.toImported(BitEvent.of(event())).orElseThrow();

        assertThat(imported.platform()).isEqualTo(BandsintownPlatform.PLATFORM);
        assertThat(imported.externalRef()).isEqualTo("108115231");
        assertThat(imported.gig().title()).isEqualTo("MOTOSRAZ HROMOVICA 2026");
        assertThat(imported.gig().location().venue()).isEqualTo("Auto Camping Dubník");
        assertThat(imported.gig().location().city()).isEqualTo("Stará Turá");
        assertThat(imported.gig().location().country()).isEqualTo(Country.SLOVAKIA);
        assertThat(imported.gig().schedule().start()).isEqualTo(ZonedDateTime.parse("2026-08-27T00:00+02:00[Europe/Bratislava]"));
        assertThat(imported.gig().schedule().end()).isEqualTo(ZonedDateTime.parse("2026-08-30T00:00+02:00[Europe/Bratislava]"));
    }

    @Test
    void a_tba_venue_and_a_missing_end_are_left_empty() {
        Map<String, Object> e = event();
        e.put("venue_name", "TBA");
        e.put("end_date", null);
        e.put("end_time", null);

        ImportedGig imported = BitEventMapper.toImported(BitEvent.of(e)).orElseThrow();

        assertThat(imported.gig().location().venue()).isNull();
        assertThat(imported.gig().schedule().end()).isNull();
    }

    @Test
    void lineup_names_are_kept_without_the_headliner() {
        Map<String, Object> e = event();
        e.put("lineup", List.of(Map.of("name", "Eufory (Band)"), Map.of("name", "Support Act"), "Other Band"));

        assertThat(BitEventMapper.toImported(BitEvent.of(e)).orElseThrow().gig().lineup()).containsExactly("Support Act", "Other Band");
    }

    @Test
    void without_a_custom_title_the_headliner_names_the_gig() {
        Map<String, Object> e = event();
        e.put("title", null);

        assertThat(BitEventMapper.toImported(BitEvent.of(e)).orElseThrow().gig().title()).isEqualTo("Eufory (Band)");
    }

    @Test
    void deleted_and_draft_events_and_events_without_a_city_are_left_out() {
        Map<String, Object> deleted = event();
        deleted.put("status", "DELETED");
        Map<String, Object> draft = event();
        draft.put("status", "DRAFT");
        Map<String, Object> noCity = event();
        noCity.put("venue_city", null);

        assertThat(BitEventMapper.toImported(BitEvent.of(deleted))).isEmpty();
        assertThat(BitEventMapper.toImported(BitEvent.of(draft))).isEmpty();
        assertThat(BitEventMapper.toImported(BitEvent.of(noCity))).isEmpty();
    }

    @Test
    void knows_the_country_names_bandsintown_uses() {
        assertThat(BitEventMapper.country("Czechia")).isEqualTo(Country.CZECHIA);
        assertThat(BitEventMapper.country("Czech Republic")).isEqualTo(Country.CZECHIA);
        assertThat(BitEventMapper.country("Germany")).isNull();
    }

    @Test
    void an_unknown_time_zone_is_read_in_the_countrys_and_an_unreadable_date_leaves_the_event_out() {
        Map<String, Object> unknownZone = event();
        unknownZone.put("venue_timezone", "Europe/Atlantis");
        Map<String, Object> badDate = event();
        badDate.put("start_date", "27.08.2026");

        assertThat(BitEventMapper.toImported(BitEvent.of(unknownZone)).orElseThrow().gig().schedule().start())
                .isEqualTo(ZonedDateTime.parse("2026-08-27T00:00+02:00[Europe/Bratislava]"));
        assertThat(BitEventMapper.toImported(BitEvent.of(badDate))).isEmpty();
    }
}
