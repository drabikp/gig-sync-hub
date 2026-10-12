package sk.drabikp.gigsynchub.gig.api;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import sk.drabikp.gigsynchub.gig.domain.Country;
import sk.drabikp.gigsynchub.gig.domain.EntryType;
import sk.drabikp.gigsynchub.gig.domain.GigDraft;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GigFormMapperTest {

    private final GigFormMapper mapper = Mappers.getMapper(GigFormMapper.class);

    private final GigDraft draft = new GigDraft("Fest",
            new GigDraft.When(LocalDate.of(2026, 10, 17), LocalTime.of(20, 30), null, LocalTime.of(2, 0),
                    LocalDate.of(2026, 10, 17), LocalTime.of(22, 0), LocalTime.of(23, 15)),
            new GigDraft.Where("Klub 007", "Luková", Country.CZECHIA, null, null, "okres Ústí nad Orlicí",
                    "Pardubický kraj", 49.876, 16.603),
            new GigDraft.About(List.of("Other band"), EntryType.PAID, "10 €", "Notes", null, null, null), false);

    /** What the page sends back after showing {@code answered} in the form. */
    private static GigFormRequest sentBack(GigFormResponse answered) {
        return new GigFormRequest(answered.getTitle(), answered.getDate(), answered.getTime(), answered.getEndDate(),
                answered.getEndTime(), answered.getSlotDate(), answered.getSlotTime(), answered.getSlotEndTime(),
                answered.getVenue(), answered.getCity(), answered.getCountry(), answered.getStreet(),
                answered.getPostalCode(), answered.getDistrict(), answered.getRegion(), answered.getLatitude(),
                answered.getLongitude(), answered.getLineup(), answered.getEntry(), answered.getPrice(),
                answered.getDescription(), answered.getFacebookUrl(), answered.getTicketUrl(), answered.getPosterUrl(),
                answered.getCancelled());
    }

    @Test
    void a_draft_goes_to_the_page_and_comes_back_unchanged() {
        GigFormResponse answered = mapper.toResponse(draft);

        assertThat(answered.getTime()).isEqualTo("20:30");
        assertThat(answered.getCountry()).isEqualTo(CountryValue.CZECHIA);
        assertThat(answered.getEntry()).isEqualTo(EntryTypeValue.PAID);
        assertThat(mapper.toDraft(sentBack(answered))).isEqualTo(draft);
    }

    @Test
    void times_with_seconds_and_missing_fields_are_read_too() {
        GigFormRequest sent = sentBack(mapper.toResponse(draft)).time("20:30:00").endTime("").country(null)
                .entry(null).cancelled(null);

        GigDraft read = mapper.toDraft(sent);

        assertThat(read.when().time()).isEqualTo(LocalTime.of(20, 30));
        assertThat(read.when().endTime()).isNull();
        assertThat(read.where().country()).isNull();
        assertThat(read.about().entry()).isEqualTo(EntryType.FREE);
        assertThat(read.cancelled()).isFalse();
    }
}
