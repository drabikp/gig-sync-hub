package sk.drabikp.gigsynchub.gig.api;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import sk.drabikp.gigsynchub.gig.domain.Country;
import sk.drabikp.gigsynchub.gig.domain.EntryType;
import sk.drabikp.gigsynchub.gig.domain.GigDraft;

import java.time.LocalTime;

/**
 * The API's shared parts (generated from gig.yaml) to the kernel's domain and back (MapStruct): a
 * gig as the form holds it ({@link GigFormRequest} → {@link GigDraft} → {@link GigFormResponse}), its
 * country and entry type. Times travel as "HH:mm" (or "HH:mm:ss"); the features' mappers use this one.
 */
@Mapper
public interface GigFormMapper {

    @Mapping(target = "when", source = "form")
    @Mapping(target = "where", source = "form")
    @Mapping(target = "about", source = "form")
    GigDraft toDraft(GigFormRequest form);

    GigDraft.When toWhen(GigFormRequest form);

    GigDraft.Where toWhere(GigFormRequest form);

    GigDraft.About toAbout(GigFormRequest form);

    @Mapping(target = ".", source = "when")
    @Mapping(target = ".", source = "where")
    @Mapping(target = ".", source = "about")
    GigFormResponse toResponse(GigDraft draft);

    Country toCountry(CountryValue value);

    CountryValue toValue(Country country);

    EntryType toEntryType(EntryTypeValue value);

    EntryTypeValue toValue(EntryType type);

    /** "20:30" or "20:30:00"; blank is no time. */
    default LocalTime toTime(String text) {
        return text == null || text.isBlank() ? null : LocalTime.parse(text);
    }

    /** A time of day as the API sends it: "20:30". */
    default String toText(LocalTime time) {
        return time == null ? null : time.toString();
    }
}
